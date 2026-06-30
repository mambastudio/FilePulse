package com.mamba.filepulse.navigator;

import com.mamba.filepulse.event.FileNavigatorEvent;
import com.mamba.filepulse.event.FileNavigatorListener;
import com.mamba.filepulse.event.FileEvent;
import com.mamba.filepulse.model.DefaultFileList;
import com.mamba.filepulse.model.FileList;
import com.mamba.filepulse.model.FileTree;
import com.mamba.filepulse.monitor.FileMonitorService;
import com.mamba.filepulse.monitor.FileMonitorService.MonitorHandle;
import com.mamba.filepulse.monitor.MonitorRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public final class DefaultFileNavigator implements FileNavigator {
    private final FileMonitorService monitor;
    private final List<FileNavigatorListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Path, MonitorHandle> expandedMonitors = new ConcurrentHashMap<>();
    private DefaultFileList currentList;
    private FileTree tree;
    private Path selectedPath;

    public DefaultFileNavigator(Path directory, FileMonitorService monitor) throws IOException {
        this.monitor = Objects.requireNonNull(monitor, "monitor must not be null");
        open(directory);
    }

    @Override
    public FileList currentList() {
        return currentList;
    }

    @Override
    public FileTree tree() {
        return tree;
    }

    @Override
    public Optional<Path> selectedPath() {
        return Optional.ofNullable(selectedPath);
    }

    @Override
    public Set<Path> expandedDirectories() {
        return Set.copyOf(expandedMonitors.keySet());
    }

    @Override
    public void open(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        stopExpandedMonitors();
        currentList = new DefaultFileList(directory);
        currentList.addListener(event -> fire(new FileNavigatorEvent.ListChanged(event)));
        tree = FileTree.scanShallow(directory);
        selectedPath = null;
        expand(directory);

        fire(new FileNavigatorEvent.DirectoryOpened(currentList.directory()));
        fire(new FileNavigatorEvent.TreeChanged(tree));
        fire(new FileNavigatorEvent.SelectionChanged(Optional.empty()));
    }

    @Override
    public FileTree expand(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        Path normalized = normalize(directory);
        expandedMonitors.computeIfAbsent(normalized,
                path -> monitor.monitor(MonitorRequest.rootOnly(path), this::onFileEvent));

        FileTree subtree = FileTree.scanShallow(normalized);
        if (tree != null) {
            tree.reload(normalized, 1);
        }
        fire(new FileNavigatorEvent.DirectoryExpanded(normalized, subtree));
        fire(new FileNavigatorEvent.TreeChanged(tree));
        return subtree;
    }

    @Override
    public void collapse(Path directory) {
        Objects.requireNonNull(directory, "directory must not be null");
        Path normalized = normalize(directory);
        expandedMonitors.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(normalized)) {
                return false;
            }
            entry.getValue().stop();
            return true;
        });
        fire(new FileNavigatorEvent.DirectoryCollapsed(normalized));
    }

    @Override
    public void select(Path path) {
        selectedPath = path == null ? null : path.toAbsolutePath().normalize();
        fire(new FileNavigatorEvent.SelectionChanged(selectedPath()));
    }

    @Override
    public void clearSelection() {
        select(null);
    }

    @Override
    public void refresh() throws IOException {
        currentList.refresh();
        tree = FileTree.scanShallow(currentList.directory());
        for (Path directory : expandedMonitors.keySet()) {
            tree.reload(directory, 1);
        }
        fire(new FileNavigatorEvent.TreeChanged(tree));
    }

    @Override
    public Path rename(Path path, String newName) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
        Path renamed = Files.move(path, path.resolveSibling(newName), StandardCopyOption.REPLACE_EXISTING);
        refresh();
        select(renamed);
        return renamed;
    }

    @Override
    public void delete(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Files.delete(path);
        if (selectedPath != null && selectedPath.equals(path.toAbsolutePath().normalize())) {
            clearSelection();
        }
        refresh();
    }

    @Override
    public Path createFolder(Path parent, String name) throws IOException {
        Objects.requireNonNull(parent, "parent must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Path created = Files.createDirectory(parent.resolve(name));
        refresh();
        select(created);
        return created;
    }

    @Override
    public void addListener(FileNavigatorListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void removeListener(FileNavigatorListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() throws IOException {
        stopExpandedMonitors();
    }

    private void onFileEvent(FileEvent event) {
        try {
            currentList.apply(event);
            if (tree != null) {
                tree.reload(event.parent(), 1);
            }
            fire(new FileNavigatorEvent.FileChanged(event));
            fire(new FileNavigatorEvent.TreeChanged(tree));
        } catch (IOException ignored) {
        }
    }

    private void stopExpandedMonitors() {
        expandedMonitors.values().forEach(MonitorHandle::stop);
        expandedMonitors.clear();
    }

    private void fire(FileNavigatorEvent event) {
        for (FileNavigatorListener listener : listeners) {
            listener.onFileNavigatorEvent(event);
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
