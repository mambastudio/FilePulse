package com.mamba.filepulse.event;

import java.nio.file.Path;

public sealed interface FileEvent {
    Path parent();

    record Created(Path parent, Path file) implements FileEvent {}
    record Deleted(Path parent, Path file) implements FileEvent {}
    record Modified(Path parent, Path file) implements FileEvent {}
    record Overflow(Path parent) implements FileEvent {}
    record KeyInvalid(Path parent) implements FileEvent {}
    record DirectoryRevalidated(Path parent) implements FileEvent {}
}
