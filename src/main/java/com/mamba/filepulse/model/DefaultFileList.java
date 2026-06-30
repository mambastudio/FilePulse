package com.mamba.filepulse.model;

import com.mamba.filepulse.event.FileEvent;
import com.mamba.filepulse.event.FileListEvent;
import com.mamba.filepulse.event.FileListListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class DefaultFileList implements FileList {
    private final List<FileListEntry> entries = new ArrayList<>();
    private final List<FileListListener> listeners = new CopyOnWriteArrayList<>();
    private Path directory;

    public DefaultFileList(Path directory) throws IOException {
        open(directory);
    }

    @Override
    public Path directory() {
        return directory;
    }

    @Override
    public List<FileListEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public Optional<FileListEntry> entry(Path path) {
        Path normalized = normalize(path);
        return entries.stream()
                .filter(entry -> normalize(entry.path()).equals(normalized))
                .findFirst();
    }

    @Override
    public void open(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        this.directory = directory.toAbsolutePath().normalize();
        loadEntries();
        fire(new FileListEvent.DirectoryChanged(this.directory));
        fire(new FileListEvent.EntriesReset(this.directory, entries()));
    }

    @Override
    public void refresh() throws IOException {
        loadEntries();
        fire(new FileListEvent.EntriesReset(directory, entries()));
    }

    @Override
    public void apply(FileEvent event) throws IOException {
        if (directory == null || !samePath(event.parent(), directory)) {
            return;
        }

        switch (event) {
            case FileEvent.Created created -> add(created.file());
            case FileEvent.Deleted deleted -> remove(deleted.file());
            case FileEvent.Modified modified -> change(modified.file());
            case FileEvent.DirectoryRevalidated ignored -> refresh();
            case FileEvent.KeyInvalid ignored -> refreshIfAvailable();
            case FileEvent.Overflow ignored -> refresh();
        }
    }

    @Override
    public void addListener(FileListListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void removeListener(FileListListener listener) {
        listeners.remove(listener);
    }

    private void loadEntries() throws IOException {
        entries.clear();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.map(this::entryOrNull)
                    .filter(Objects::nonNull)
                    .sorted(FileListEntry.DIRECTORIES_FIRST)
                    .forEach(entries::add);
        }
    }

    private FileListEntry entryOrNull(Path path) {
        try {
            return FileListEntry.from(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void add(Path path) throws IOException {
        if (!isDirectChild(path) || entry(path).isPresent() || !Files.exists(path)) {
            return;
        }

        FileListEntry added = FileListEntry.from(path);
        entries.add(added);
        entries.sort(FileListEntry.DIRECTORIES_FIRST);
        fire(new FileListEvent.EntryAdded(added));
    }

    private void remove(Path path) {
        int index = indexOf(path);
        if (index < 0) {
            return;
        }

        FileListEntry removed = entries.remove(index);
        fire(new FileListEvent.EntryRemoved(removed));
    }

    private void change(Path path) throws IOException {
        int index = indexOf(path);
        if (index < 0 || !Files.exists(path)) {
            return;
        }

        FileListEntry oldEntry = entries.get(index);
        FileListEntry newEntry = FileListEntry.from(path);
        entries.set(index, newEntry);
        entries.sort(FileListEntry.DIRECTORIES_FIRST);
        fire(new FileListEvent.EntryChanged(oldEntry, newEntry));
    }

    private int indexOf(Path path) {
        Path normalized = normalize(path);
        for (int i = 0; i < entries.size(); i++) {
            if (normalize(entries.get(i).path()).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isDirectChild(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent != null && parent.equals(directory);
    }

    private void refreshIfAvailable() throws IOException {
        if (Files.isDirectory(directory)) {
            refresh();
        } else {
            entries.clear();
            fire(new FileListEvent.EntriesReset(directory, Collections.emptyList()));
        }
    }

    private void fire(FileListEvent event) {
        for (FileListListener listener : listeners) {
            listener.onFileListEvent(event);
        }
    }

    private static boolean samePath(Path first, Path second) {
        return normalize(first).equals(normalize(second));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
