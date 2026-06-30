package com.mamba.filepulse.event;

@FunctionalInterface
public interface FileEventListener {
    void onEvent(FileEvent event);
}
