package com.mamba.filepulse.model;

import com.mamba.filepulse.event.FileEvent;
import com.mamba.filepulse.event.FileListListener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface FileList {
    Path directory();

    List<FileListEntry> entries();

    Optional<FileListEntry> entry(Path path);

    void open(Path directory) throws IOException;

    void refresh() throws IOException;

    void apply(FileEvent event) throws IOException;

    void addListener(FileListListener listener);

    void removeListener(FileListListener listener);
}
