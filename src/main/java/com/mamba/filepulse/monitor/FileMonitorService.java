package com.mamba.filepulse.monitor;

import com.mamba.filepulse.monitor.FileMonitorState.FileRefMeta;
import com.mamba.filepulse.event.FileEventListener;
import com.mamba.filepulse.model.FileTree;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface FileMonitorService extends AutoCloseable {

    default MonitorHandle monitor(Path root, FileEventListener listener) {
        return monitor(MonitorRequest.recursive(root), listener);
    }

    MonitorHandle monitor(MonitorRequest request, FileEventListener listener);

    default MonitorHandle monitorTree(FileTree tree, FileEventListener listener) {
        return monitor(MonitorRequest.predeterminedTree(tree), listener);
    }

    int monitoredCount();

    Map<Path, FileRefMeta> metaMap();

    @Override
    void close() throws IOException;

    interface MonitorHandle extends AutoCloseable {
        void stop();

        @Override
        default void close() {
            stop();
        }
    }
}
