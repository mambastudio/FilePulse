package com.mamba.filepulse.event;

import com.mamba.filepulse.model.FileListEntry;
import java.nio.file.Path;
import java.util.List;

public sealed interface FileListEvent {
    record DirectoryChanged(Path directory) implements FileListEvent {}
    record EntryAdded(FileListEntry entry) implements FileListEvent {}
    record EntryRemoved(FileListEntry entry) implements FileListEvent {}
    record EntryChanged(FileListEntry oldEntry, FileListEntry newEntry) implements FileListEvent {}
    record EntriesReset(Path directory, List<FileListEntry> entries) implements FileListEvent {}
}
