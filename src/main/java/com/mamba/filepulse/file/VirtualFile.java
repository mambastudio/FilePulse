package com.mamba.filepulse.file;

public record VirtualFile(String name) implements FileRef {
    public VirtualFile {
        if (name == null || name.isBlank()) {
            name = "virtual";
        }
    }

    public VirtualFile() {
        this("virtual");
    }

    @Override
    public boolean isVirtual() {
        return true;
    }
}
