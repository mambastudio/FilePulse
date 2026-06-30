package com.mamba.filepulse.event;

@FunctionalInterface
public interface FileNavigatorListener {
    void onFileNavigatorEvent(FileNavigatorEvent event);
}
