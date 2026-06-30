package com.mamba.filepulse.file;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;

public record RealFile(Path path) implements FileRef {
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    public RealFile {
        Objects.requireNonNull(path, "path must not be null");
    }

    public RealFile(String path) {
        this(path == null ? null : Paths.get(path));
    }

    public RealFile(File file) {
        this(file == null ? null : file.toPath());
    }

    public RealFile(URI uri) {
        this(uri == null ? null : Paths.get(uri));
    }

    public boolean exists() {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isDirectory() {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isRegularFile() {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isLeaf() {
        return !isDirectory();
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public String name() {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RealFile other)) {
            return false;
        }

        if (IS_WINDOWS) {
            return path.normalize().toString()
                    .equalsIgnoreCase(other.path.normalize().toString());
        }
        return path.normalize().equals(other.path.normalize());
    }

    @Override
    public final int hashCode() {
        String normalized = path.normalize().toString();
        return IS_WINDOWS ? normalized.toLowerCase(Locale.ROOT).hashCode() : normalized.hashCode();
    }

    @Override
    public String toString() {
        return name();
    }
}
