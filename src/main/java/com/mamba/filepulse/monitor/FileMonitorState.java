package com.mamba.filepulse.monitor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FileMonitorState {
    private final Map<Path, FileRefMeta> metaMap;

    public FileMonitorState() {
        this(new ConcurrentHashMap<>());
    }

    public FileMonitorState(Map<Path, FileRefMeta> metaMap) {
        this.metaMap = Objects.requireNonNull(metaMap, "metaMap must not be null");
    }

    public Map<Path, FileRefMeta> metaMap() {
        return metaMap;
    }

    public void clearMeta(Path path) {
        metaMap.remove(path);
    }

    public interface FileRefMeta {}
}
