package com.mamba.filepulse.monitor;

import com.mamba.filepulse.monitor.FileMonitorService.MonitorHandle;
import com.mamba.filepulse.monitor.FileMonitorState.FileRefMeta;
import com.mamba.filepulse.event.FileEvent;
import com.mamba.filepulse.event.FileEventListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PollingFileMonitor implements FileMonitorService {
    private final ScheduledExecutorService executor;
    private final AtomicInteger count = new AtomicInteger();
    private final FileMonitorState monitorState;
    private final FileMonitorOptions options;

    public PollingFileMonitor() {
        this(FileMonitorOptions.DEFAULT);
    }

    public PollingFileMonitor(long intervalMillis) {
        this(FileMonitorOptions.DEFAULT.withIntervalMillis(intervalMillis));
    }

    public PollingFileMonitor(FileMonitorOptions options) {
        this(options, new FileMonitorState());
    }

    public PollingFileMonitor(FileMonitorOptions options, FileMonitorState monitorState) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.monitorState = Objects.requireNonNull(monitorState, "monitorState must not be null");
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "PollingFileMonitor-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public MonitorHandle monitor(MonitorRequest request, FileEventListener listener) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        var targets = request.pollingTargets();
        var directories = request.watchDirectories();
        if (targets.isEmpty()) {
            return () -> {};
        }

        var handle = new PollingHandle(request, targets, directories, listener);
        handle.start();
        count.incrementAndGet();
        return handle;
    }

    @Override
    public int monitoredCount() {
        return count.get();
    }

    @Override
    public Map<Path, FileRefMeta> metaMap() {
        return monitorState.metaMap();
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
    }

    private Map<Path, FileSnapshot> snapshot(MonitorRequest request, List<Path> roots) {
        var snapshot = new HashMap<Path, FileSnapshot>();
        for (var root : roots) {
            snapshot.putAll(snapshot(request, root));
        }
        return snapshot;
    }

    private Map<Path, FileSnapshot> snapshot(MonitorRequest request, Path root) {
        var snapshot = new HashMap<Path, FileSnapshot>();
        if (!Files.exists(root)) {
            return snapshot;
        }

        if (Files.isRegularFile(root)) {
            if (accepts(root)) {
                snapshotPath(root, snapshot);
            }
            return snapshot;
        }

        if (!Files.isDirectory(root)) {
            return snapshot;
        }

        if (request.scope() == MonitorScope.RECURSIVE) {
            try (var stream = Files.walk(root)) {
                stream.filter(path -> !path.equals(root))
                        .filter(this::accepts)
                        .forEach(path -> snapshotPath(path, snapshot));
            } 
            catch (IOException ignored) {
            }
            return snapshot;
        }

        try (var stream = Files.list(root)) {
            stream.filter(this::accepts)
                    .forEach(path -> snapshotPath(path, snapshot));
        } 
        catch (IOException ignored) {
        }

        return snapshot;
    }

    private boolean accepts(Path path) {
        return Files.isDirectory(path)
                || options.extensions().accepts(path.getFileName().toString());
    }

    private void snapshotPath(Path path, Map<Path, FileSnapshot> snapshot) {
        try {
            snapshot.put(path, new FileSnapshot(
                    Files.isDirectory(path),
                    Files.getLastModifiedTime(path).toMillis(),
                    Files.isDirectory(path) ? 0L : Files.size(path)));
        } 
        catch (IOException ignored) {
        }
    }

    private final class PollingHandle implements MonitorHandle {
        private final MonitorRequest request;
        private final List<Path> roots;
        private final List<Path> directories;
        private final FileEventListener listener;
        private final Map<Path, Boolean> directoryAvailability;
        private final Map<Path, Long> missingSince = new HashMap<>();
        private final Set<Path> expiredDirectories = new HashSet<>();
        private volatile Map<Path, FileSnapshot> previous;
        private ScheduledFuture<?> future;
        private volatile boolean stopped;

        PollingHandle(MonitorRequest request, List<Path> roots, List<Path> directories, FileEventListener listener) {
            this.request = request;
            this.roots = List.copyOf(roots);
            this.directories = List.copyOf(directories);
            this.listener = listener;
            this.directoryAvailability = directoryAvailability(directories);
            this.previous = snapshot(request, roots);
        }

        void start() {
            future = executor.scheduleWithFixedDelay(this::poll,
                    options.intervalMillis(), options.intervalMillis(), TimeUnit.MILLISECONDS);
        }

        private void poll() {
            if (stopped) {
                return;
            }

            emitDirectoryAvailabilityChanges();
            var current = snapshot(request, roots);
            emitChanges(previous, current);
            previous = current;
        }

        private Map<Path, Boolean> directoryAvailability(List<Path> directories) {
            var availability = new HashMap<Path, Boolean>();
            for (var directory : directories) {
                availability.put(directory, Files.isDirectory(directory));
            }
            return availability;
        }

        private void emitDirectoryAvailabilityChanges() {
            var missing     = new ArrayList<Path>();
            var recovered   = new ArrayList<Path>();
            var now         = System.currentTimeMillis();

            for (var directory : directories) {
                if (expiredDirectories.contains(directory)) {
                    continue;
                }

                var wasAvailable = directoryAvailability.getOrDefault(directory, false);
                var available = Files.isDirectory(directory);

                if (!available) {
                    var missingFrom = missingSince.get(directory);
                    if (missingFrom != null && now - missingFrom >= options.missingRetentionMillis()) {
                        expiredDirectories.add(directory);
                        missingSince.remove(directory);
                        directoryAvailability.remove(directory);
                        monitorState.clearMeta(directory);
                        continue;
                    }
                }

                if (wasAvailable == available) {
                    continue;
                }

                directoryAvailability.put(directory, available);
                if (available) {
                    missingSince.remove(directory);
                    recovered.add(directory);
                } else {
                    missingSince.put(directory, now);
                    missing.add(directory);
                }
            }

            missing.stream()
                    .sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                    .map(FileEvent.KeyInvalid::new)
                    .forEach(listener::onEvent);

            recovered.stream()
                    .sorted((a, b) -> Integer.compare(a.getNameCount(), b.getNameCount()))
                    .map(FileEvent.DirectoryRevalidated::new)
                    .forEach(listener::onEvent);
        }

        private void emitChanges(Map<Path, FileSnapshot> oldSnapshot, Map<Path, FileSnapshot> newSnapshot) {
            var all = new HashSet<Path>();
            all.addAll(oldSnapshot.keySet());
            all.addAll(newSnapshot.keySet());

            for (var file : all) {
                var oldFile = oldSnapshot.get(file);
                var newFile = newSnapshot.get(file);
                var parent = file.getParent();

                if (oldFile == null && newFile != null) {
                    listener.onEvent(new FileEvent.Created(parent, file));
                } else if (oldFile != null && newFile == null) {
                    listener.onEvent(new FileEvent.Deleted(parent, file));
                } else if (!Objects.equals(oldFile, newFile)) {
                    listener.onEvent(new FileEvent.Modified(parent, file));
                }
            }
        }

        @Override
        public void stop() {
            if (stopped) {
                return;
            }

            stopped = true;
            if (future != null) {
                future.cancel(false);
            }
            count.decrementAndGet();
        }
    }

    private record FileSnapshot(boolean directory, long modifiedMillis, long size) {}
}
