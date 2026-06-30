package com.mamba.filepulse.model;

import com.mamba.filepulse.file.RealFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;

public record FileListEntry(
        RealFile file,
        boolean directory,
        boolean readable,
        long size,
        Instant modifiedTime) {

    public static final Comparator<FileListEntry> DIRECTORIES_FIRST =
            Comparator.comparing(FileListEntry::directory).reversed()
                    .thenComparing(entry -> entry.name().toLowerCase());

    public static FileListEntry from(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        return new FileListEntry(
                new RealFile(path),
                attributes.isDirectory(),
                Files.isReadable(path),
                attributes.isDirectory() ? 0L : attributes.size(),
                attributes.lastModifiedTime().toInstant());
    }

    public Path path() {
        return file.path();
    }

    public String name() {
        return file.name();
    }
}
