package com.mamba.filepulse.event;

import com.mamba.filepulse.model.FileTree;
import java.nio.file.Path;
import java.util.Optional;

public sealed interface FileNavigatorEvent {
    record DirectoryOpened(Path directory) implements FileNavigatorEvent {}
    record DirectoryExpanded(Path directory, FileTree subtree) implements FileNavigatorEvent {}
    record DirectoryCollapsed(Path directory) implements FileNavigatorEvent {}
    record SelectionChanged(Optional<Path> selected) implements FileNavigatorEvent {}
    record TreeChanged(FileTree tree) implements FileNavigatorEvent {}
    record ListChanged(FileListEvent event) implements FileNavigatorEvent {}
    record FileChanged(FileEvent event) implements FileNavigatorEvent {}
}
