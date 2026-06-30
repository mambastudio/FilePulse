package com.mamba.filepulse.monitor;

import com.mamba.filepulse.monitor.FileMonitorService.MonitorHandle;
import com.mamba.filepulse.monitor.FileMonitorState.FileRefMeta;
import com.mamba.filepulse.event.FileEventListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public class HybridFileMonitorService implements FileMonitorService {
    public enum Backend {
        NATIVE,
        POLLING
    }

    @FunctionalInterface
    public interface MonitorPolicy {
        Backend backendFor(MonitorRequest request);
    }

    private final NativeFileMonitor nativeMonitor;
    private final PollingFileMonitor pollingMonitor;
    private final MonitorPolicy policy;
    private final FileMonitorState monitorState;

    public HybridFileMonitorService() {
        this(oneDriveUsesPolling());
    }

    public HybridFileMonitorService(MonitorPolicy policy) {
        this(policy, FileMonitorOptions.DEFAULT);
    }

    public HybridFileMonitorService(MonitorPolicy policy, FileMonitorOptions options) {
        this(policy, options, new FileMonitorState());
    }

    public HybridFileMonitorService(
            MonitorPolicy policy,
            FileMonitorOptions options,
            FileMonitorState monitorState) {
        this(new NativeFileMonitor(options, monitorState),
                new PollingFileMonitor(options, monitorState),
                policy,
                monitorState);
    }

    public HybridFileMonitorService(
            NativeFileMonitor nativeMonitor,
            PollingFileMonitor pollingMonitor,
            MonitorPolicy policy,
            FileMonitorState monitorState) {
        this.nativeMonitor = Objects.requireNonNull(nativeMonitor, "nativeMonitor must not be null");
        this.pollingMonitor = Objects.requireNonNull(pollingMonitor, "pollingMonitor must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.monitorState = Objects.requireNonNull(monitorState, "monitorState must not be null");
    }

    public static MonitorPolicy nativeBackend() {
        return tree -> Backend.NATIVE;
    }

    public static MonitorPolicy pollingBackend() {
        return tree -> Backend.POLLING;
    }

    public static MonitorPolicy oneDriveUsesPolling() {
        return request -> request.isOneDrivePath() ? Backend.POLLING : Backend.NATIVE;
    }

    @Override
    public MonitorHandle monitor(MonitorRequest request, FileEventListener listener) {
        return switch (policy.backendFor(request)) {
            case NATIVE -> nativeMonitor.monitor(request, listener);
            case POLLING -> pollingMonitor.monitor(request, listener);
        };
    }

    @Override
    public int monitoredCount() {
        return nativeMonitor.monitoredCount() + pollingMonitor.monitoredCount();
    }

    @Override
    public Map<Path, FileRefMeta> metaMap() {
        return monitorState.metaMap();
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            nativeMonitor.close();
        } catch (IOException ex) {
            failure = ex;
        }
        try {
            pollingMonitor.close();
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
