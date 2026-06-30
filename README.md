# FilePulse

FilePulse is a small Java library for monitoring files and directories through a simple event-based API.

It wraps Java's native watch service with a polling fallback, making it easier to build tools that react to file creation, deletion, modification, and directory refresh events.

## Features

- Recursive, root-only, explicit-list, and predetermined-tree monitoring modes
- Hybrid backend that can use native file watching or polling
- Polling fallback for OneDrive paths
- File tree and file list models for navigator-style UIs
- File navigator abstraction for opening, expanding, selecting, renaming, deleting, and creating folders
- Simple sealed event types for file and navigator changes

## Requirements

- Java 25
- Maven 3.9 or newer

## Build

```bash
mvn test
```

```bash
mvn package
```

The compiled jar is generated under `target/`.

## CLI Usage

Run the monitor against the current directory:

```bash
mvn exec:java
```

Monitor a specific directory:

```bash
mvn exec:java -Dexec.args="C:/path/to/folder"
```

Choose a monitor scope:

```bash
mvn exec:java -Dexec.args="--scope=recursive C:/path/to/folder"
```

Supported scopes:

- `recursive`
- `root-only`
- `predetermined-tree`
- `explicit-list`

## Library Example

```java
import com.mamba.filepulse.monitor.HybridFileMonitorService;
import java.nio.file.Path;

public class Example {
    public static void main(String[] args) throws Exception {
        try (HybridFileMonitorService monitor = new HybridFileMonitorService()) {
            monitor.monitor(Path.of("."), System.out::println);
            Thread.currentThread().join();
        }
    }
}
```

## Project Status

FilePulse is currently an early-stage library and API experiment. The public APIs may still change before a stable release.
