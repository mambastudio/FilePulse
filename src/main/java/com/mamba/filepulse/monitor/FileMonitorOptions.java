package com.mamba.filepulse.monitor;

public record FileMonitorOptions(
        long eventDelayMillis,
        long intervalMillis,
        long missingRetentionMillis,
        FileExtensions extensions) {

    public static final FileMonitorOptions DEFAULT =
            new FileMonitorOptions(100, 1_000, 60_000, new FileExtensions());

    public FileMonitorOptions {
        if (eventDelayMillis < 0) {
            throw new IllegalArgumentException("eventDelayMillis must not be negative");
        }
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("intervalMillis must be positive");
        }
        if (missingRetentionMillis < 0) {
            throw new IllegalArgumentException("missingRetentionMillis must not be negative");
        }
        if (extensions == null) {
            extensions = new FileExtensions();
        }
    }

    public FileMonitorOptions withEventDelayMillis(long millis) {
        return new FileMonitorOptions(millis, intervalMillis, missingRetentionMillis, extensions);
    }

    public FileMonitorOptions withIntervalMillis(long millis) {
        return new FileMonitorOptions(eventDelayMillis, millis, missingRetentionMillis, extensions);
    }

    public FileMonitorOptions withMissingRetentionMillis(long millis) {
        return new FileMonitorOptions(eventDelayMillis, intervalMillis, millis, extensions);
    }

    public FileMonitorOptions withExtensions(String... extensions) {
        return new FileMonitorOptions(eventDelayMillis, intervalMillis, missingRetentionMillis,
                new FileExtensions(extensions));
    }
}
