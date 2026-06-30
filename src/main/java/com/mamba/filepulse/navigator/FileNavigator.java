package com.mamba.filepulse.navigator;

import com.mamba.filepulse.event.FileNavigatorListener;
import com.mamba.filepulse.model.FileList;
import com.mamba.filepulse.model.FileTree;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public interface FileNavigator extends AutoCloseable {
    FileList currentList();

    FileTree tree();

    Optional<Path> selectedPath();

    Set<Path> expandedDirectories();

    void open(Path directory) throws IOException;

    FileTree expand(Path directory) throws IOException;

    void collapse(Path directory);

    void select(Path path);

    void clearSelection();

    void refresh() throws IOException;

    Path rename(Path path, String newName) throws IOException;

    void delete(Path path) throws IOException;

    Path createFolder(Path parent, String name) throws IOException;

    void addListener(FileNavigatorListener listener);

    void removeListener(FileNavigatorListener listener);

    @Override
    void close() throws IOException;
}
