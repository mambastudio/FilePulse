package com.mamba.filepulse.file;

import java.util.Objects;
import java.util.Optional;

public sealed interface FileRef permits RealFile, VirtualFile {
    boolean isVirtual();

    String name();

    default boolean isAncestorOf(FileRef child) {
        Objects.requireNonNull(child, "child must not be null");
        return switch (this) {
            case VirtualFile _ -> true;
            case RealFile parent -> switch (child) {
                case VirtualFile _ -> false;
                case RealFile real -> !parent.equals(real)
                        && real.path().normalize().startsWith(parent.path().normalize());
            };
        };
    }

    default boolean isDescendantOf(FileRef parent) {
        return parent != null && parent.isAncestorOf(this);
    }

    default Optional<RealFile> parent() {
        return switch (this) {
            case VirtualFile _ -> Optional.empty();
            case RealFile real -> Optional.ofNullable(real.path().getParent()).map(RealFile::new);
        };
    }
}
