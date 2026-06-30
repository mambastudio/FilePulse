package com.mamba.filepulse.monitor;

import com.mamba.filepulse.monitor.FileMonitorService.MonitorHandle;
import com.mamba.filepulse.monitor.FileMonitorState.FileRefMeta;
import com.mamba.filepulse.event.FileEvent;
import com.mamba.filepulse.event.FileEventListener;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeFileMonitor implements FileMonitorService {
    private static final class DirState {
        private WatchKey key;
        private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    }

    private record Registration(FileEventListener listener, MonitorRequest request) {}

    private record InvalidationInfo(long timestamp, List<Registration> retainedRegistrations) {}

    private final WatchService watcher;
    private final ScheduledExecutorService scheduler;
    private final Map<Path, DirState> states = new ConcurrentHashMap<>();
    private final Map<Path, InvalidationInfo> invalidations = new ConcurrentHashMap<>();
    private final FileMonitorState monitorState;
    private final Thread thread;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile FileMonitorOptions options;

    public NativeFileMonitor() {
        this(FileMonitorOptions.DEFAULT);
    }

    public NativeFileMonitor(FileMonitorOptions options) {
        this(options, new FileMonitorState());
    }

    public NativeFileMonitor(FileMonitorOptions options, FileMonitorState monitorState) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.monitorState = Objects.requireNonNull(monitorState, "monitorState must not be null");
        try {
            this.watcher = FileSystems.getDefault().newWatchService();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create WatchService", ex);
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "NativeFileMonitor-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.thread = new Thread(this::processEvents, "NativeFileMonitor-Thread");
        this.thread.setDaemon(true);
    }

    public static NativeFileMonitor start(FileMonitorOptions options) {
        var monitor = new NativeFileMonitor(options);
        monitor.start();
        return monitor;
    }

    private void start() {
        if (started.compareAndSet(false, true)) 
            thread.start();        
    }

    @Override
    public MonitorHandle monitor(MonitorRequest request, FileEventListener listener) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        var watched = new ArrayList<Path>();
        var registration = new Registration(listener, request);
        for (var directory : request.watchDirectories()) {
            watch(directory, registration);
            watched.add(directory);
        }
        start();
        return () -> watched.forEach(directory -> unwatch(directory, registration));
    }

    public void watch(Path directory, FileEventListener listener) {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        watch(directory, new Registration(listener, MonitorRequest.rootOnly(directory)));
        start();
    }

    private void watch(Path directory, Registration registration) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        states.computeIfAbsent(directory.normalize(), this::registerDir)
                .registrations.add(registration);
    }

    public void unwatch(Path directory, FileEventListener... listeners) {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(listeners, "listeners must not be null");
        var normalised = directory.normalize();
        if (!(states.get(normalised) instanceof DirState state)) {
            return;
        }

        var removeAll = listeners.length == 0;
        if (!removeAll) {
            var listenerList = List.of(listeners);
            state.registrations.removeIf(registration -> listenerList.contains(registration.listener()));
            removeAll = state.registrations.isEmpty();
        }

        if (removeAll) {
            if (states.remove(normalised) instanceof DirState removed && removed.key != null) {
                removed.key.cancel();
            }
        }
    }

    private void unwatch(Path directory, Registration registration) {
        Objects.requireNonNull(directory, "directory must not be null");
        var normalised = directory.normalize();
        if (!(states.get(normalised) instanceof DirState state)) {
            return;
        }

        state.registrations.remove(registration);
        if (state.registrations.isEmpty()) {
            if (states.remove(normalised) instanceof DirState removed && removed.key != null) {
                removed.key.cancel();
            }
        }
    }

    public boolean isWatched(Path directory) {
        return states.containsKey(directory.normalize());
    }

    @Override
    public int monitoredCount() {
        return states.size();
    }

    @Override
    public Map<Path, FileRefMeta> metaMap() {
        return monitorState.metaMap();
    }

    public FileMonitorOptions options() {
        return options;
    }

    public void setOptions(FileMonitorOptions options) {
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    private DirState registerDir(Path directory) {
        try {
            DirState state = new DirState();
            state.key = directory.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            return state;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to register watcher for " + directory, ex);
        }
    }

    private void processEvents() {
        try {
            while (true) {
                var key = watcher.take();
                var directory = ((Path) key.watchable()).normalize();

                if (states.get(directory) instanceof DirState state) {
                    for (var event : key.pollEvents()) {
                        FileEvent fileEvent = toFileEvent(directory, event);
                        registerCreatedDirectory(fileEvent, state.registrations);
                        dispatch(fileEvent, state.registrations);
                    }

                    if (!key.reset()) {
                        handleInvalidKey(directory, state);
                    }
                } else if (!key.reset()) {
                    handleInvalidKey(directory, null);
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void registerCreatedDirectory(FileEvent event, List<Registration> registrations) {
        if (event instanceof FileEvent.Created created && Files.isDirectory(created.file())) {
            var recursiveRegistrations = registrations.stream()
                    .filter(registration -> registration.request().scope() == MonitorScope.RECURSIVE)
                    .toList();
            if (!recursiveRegistrations.isEmpty()) {
                registerDirectoryTree(created.file(), recursiveRegistrations);
            }
        }
    }

    private void registerDirectoryTree(Path root, List<Registration> registrations) {
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                    .forEach(directory -> states
                            .computeIfAbsent(directory.normalize(), this::registerDir)
                            .registrations.addAll(registrations));
        } catch (IOException ignored) {
            states.computeIfAbsent(root.normalize(), this::registerDir)
                    .registrations.addAll(registrations);
        }
    }

    private boolean accepts(FileEvent event) {
        return switch (Objects.requireNonNull(event, "event must not be null")) {
            case FileEvent.Created created -> accepts(created.file());
            case FileEvent.Modified modified -> accepts(modified.file());
            case FileEvent.Deleted deleted -> states.containsKey(deleted.file().normalize())
                    || options.extensions().accepts(deleted.file().getFileName().toString());
            default -> true;
        };
    }

    private boolean accepts(Path path) {
        return Files.isDirectory(path) || options.extensions().accepts(path.getFileName().toString());
    }

    private void dispatch(FileEvent event, List<Registration> registrations) {
        FileMonitorOptions currentOptions = options;
        for (Registration registration : registrations) {
            if (accepts(event) && registration.request().explicitlyContains(eventPath(event))) {
                scheduler.schedule(() -> registration.listener().onEvent(event),
                        currentOptions.eventDelayMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private Path eventPath(FileEvent event) {
        return switch (Objects.requireNonNull(event, "event must not be null")) {
            case FileEvent.Created created -> created.file();
            case FileEvent.Modified modified -> modified.file();
            case FileEvent.Deleted deleted -> deleted.file();
            default -> event.parent();
        };
    }

    private void handleInvalidKey(Path directory, DirState state) {
        if (!(state instanceof DirState invalidState)) {
            states.remove(directory);
            return;
        }

        invalidations.put(directory, new InvalidationInfo(
                System.currentTimeMillis(),
                List.copyOf(invalidState.registrations)));
        dispatch(new FileEvent.KeyInvalid(directory), invalidState.registrations);
        states.remove(directory);
        scheduler.schedule(() -> revalidate(directory), options.intervalMillis(), TimeUnit.MILLISECONDS);
    }

    private void revalidate(Path directory) {
        if (!(invalidations.get(directory) instanceof InvalidationInfo info)) 
            return;

        if (Files.isDirectory(directory)) {
            var state = registerDir(directory);
            state.registrations.addAll(info.retainedRegistrations());
            states.put(directory, state);
            invalidations.remove(directory);
            dispatch(new FileEvent.DirectoryRevalidated(directory), state.registrations);
            return;
        }

        long age = System.currentTimeMillis() - info.timestamp();
        var currentOptions = options;
        if (age < currentOptions.missingRetentionMillis()) {
            scheduler.schedule(() -> revalidate(directory),
                    currentOptions.intervalMillis(), TimeUnit.MILLISECONDS);
        } else {
            invalidations.remove(directory);
            monitorState.clearMeta(directory);
        }
    }

    @SuppressWarnings("unchecked")
    private FileEvent toFileEvent(Path parent, WatchEvent<?> event) {
        var kind = event.kind();
        return switch (kind) {
            case WatchEvent.Kind<?> k when k == OVERFLOW ->     new FileEvent.Overflow(parent);
            case WatchEvent.Kind<?> k when k == ENTRY_CREATE -> new FileEvent.Created(parent, parent.resolve(((WatchEvent<Path>) event).context()));
            case WatchEvent.Kind<?> k when k == ENTRY_DELETE -> new FileEvent.Deleted(parent, parent.resolve(((WatchEvent<Path>) event).context()));
            case WatchEvent.Kind<?> k when k == ENTRY_MODIFY -> new FileEvent.Modified(parent, parent.resolve(((WatchEvent<Path>) event).context()));
            default -> throw new IllegalArgumentException("Unknown event: " + kind);
        };
    }

    @Override
    public void close() throws IOException {
        scheduler.shutdownNow();
        try {
            watcher.close();
        } 
        finally {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
