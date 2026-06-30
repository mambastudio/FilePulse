package com.mamba.filepulse;

import com.mamba.filepulse.monitor.FileMonitorOptions;
import com.mamba.filepulse.monitor.HybridFileMonitorService;
import com.mamba.filepulse.monitor.MonitorRequest;
import com.mamba.filepulse.monitor.MonitorScope;
import com.mamba.filepulse.event.FileEvent;
import com.mamba.filepulse.model.FileTree;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

public class FilePulse {

    public static void main(String[] args) throws Exception {
        CliRequest cli = parse(args);
        FileMonitorOptions options = FileMonitorOptions.DEFAULT;

        try (HybridFileMonitorService monitor = new HybridFileMonitorService(
                HybridFileMonitorService.oneDriveUsesPolling(),
                options)) {
            monitor.monitor(cli.request(), FilePulse::printEvent);
            System.out.println("Monitoring " + cli.paths());
            System.out.println("Scope " + cli.scope());
            System.out.println("Press Ctrl+C to stop.");
            Thread.currentThread().join();
        }
    }

    private static CliRequest parse(String[] args) {
        MonitorScope scope = MonitorScope.RECURSIVE;
        List<Path> paths = new ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("--scope=")) {
                scope = MonitorScope.valueOf(arg.substring("--scope=".length())
                        .replace('-', '_')
                        .toUpperCase());
            } else {
                paths.add(Path.of(arg));
            }
        }

        if (paths.isEmpty()) {
            paths.add(Path.of("."));
        }

        MonitorRequest request = switch (scope) {
            case ROOT_ONLY -> MonitorRequest.rootOnly(paths);
            case RECURSIVE -> MonitorRequest.recursive(paths);
            case PREDETERMINED_TREE -> MonitorRequest.predeterminedTree(
                    paths.size() == 1
                            ? FileTree.scan(paths.getFirst())
                            : FileTree.virtualRoot("roots", paths));
            case EXPLICIT_LIST -> MonitorRequest.explicit(paths);
        };
        return new CliRequest(scope, paths, request);
    }

    private static void printEvent(FileEvent event) {
        System.out.println(event);
    }

    private record CliRequest(MonitorScope scope, List<Path> paths, MonitorRequest request) {}
}
