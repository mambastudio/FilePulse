package com.mamba.filepulse.event;

@FunctionalInterface
public interface FileListListener {
    void onFileListEvent(FileListEvent event);
}
