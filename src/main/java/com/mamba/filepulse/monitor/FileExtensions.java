package com.mamba.filepulse.monitor;

import java.util.Arrays;

public record FileExtensions(String... extensions) {
    public FileExtensions {
        if (extensions == null) {
            extensions = new String[0];
        }
        extensions = Arrays.stream(extensions)
                .filter(ext -> ext != null && !ext.isBlank())
                .map(ext -> ext.startsWith(".") ? ext : "." + ext)
                .map(String::toLowerCase)
                .distinct()
                .toArray(String[]::new);
    }

    public boolean hasExtensions() {
        return extensions.length > 0;
    }

    public boolean accepts(String fileName) {
        if (!hasExtensions()) {
            return true;
        }

        String lower = fileName.toLowerCase();
        return Arrays.stream(extensions).anyMatch(lower::endsWith);
    }
}
