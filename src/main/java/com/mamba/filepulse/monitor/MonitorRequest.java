package com.mamba.filepulse.monitor;

import com.mamba.filepulse.file.RealFile;
import com.mamba.filepulse.model.FileTree;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record MonitorRequest(
        MonitorScope scope,
        List<Path> paths,
        FileTree tree) {

    public MonitorRequest {
        Objects.requireNonNull(scope, "scope must not be null");
        paths = paths == null ? List.of() : List.copyOf(paths);
    }

    public static MonitorRequest rootOnly(Path root) {
        return rootOnly(List.of(root));
    }

    public static MonitorRequest rootOnly(Collection<Path> roots) {
        return new MonitorRequest(MonitorScope.ROOT_ONLY, List.copyOf(roots), null);
    }

    public static MonitorRequest recursive(Path root) {
        return recursive(List.of(root));
    }

    public static MonitorRequest recursive(Collection<Path> roots) {
        return new MonitorRequest(MonitorScope.RECURSIVE, List.copyOf(roots), null);
    }

    public static MonitorRequest predeterminedTree(FileTree tree) {
        return new MonitorRequest(MonitorScope.PREDETERMINED_TREE, List.of(),
                Objects.requireNonNull(tree, "tree must not be null"));
    }

    public static MonitorRequest explicit(Collection<Path> paths) {
        return new MonitorRequest(MonitorScope.EXPLICIT_LIST, List.copyOf(paths), null);
    }

    public List<Path> watchDirectories() {
        return switch (scope) {
            case ROOT_ONLY, RECURSIVE -> existingDirectories(paths);
            case PREDETERMINED_TREE -> tree == null ? List.of() : tree.directories();
            case EXPLICIT_LIST -> explicitWatchDirectories();
        };
    }

    public List<Path> pollingTargets() {
        return switch (scope) {
            case ROOT_ONLY, RECURSIVE, EXPLICIT_LIST -> paths;
            case PREDETERMINED_TREE -> tree == null ? List.of() : tree.directories();
        };
    }

    public List<Path> realRoots() {
        if (scope == MonitorScope.PREDETERMINED_TREE && tree != null) {
            return tree.realRoots();
        }
        return paths;
    }

    private List<Path> explicitWatchDirectories() {
        List<Path> directories = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                directories.add(path);
            } else if (path.getParent() != null) {
                directories.add(path.getParent());
            }
        }
        return directories.stream().distinct().toList();
    }

    private static List<Path> existingDirectories(List<Path> paths) {
        return paths.stream()
                .filter(Files::isDirectory)
                .distinct()
                .toList();
    }

    public boolean explicitlyContains(Path path) {
        if (scope != MonitorScope.EXPLICIT_LIST) {
            return true;
        }

        Path normalized = path.toAbsolutePath().normalize();
        for (Path candidate : paths) {
            Path candidateNormalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(candidate)) {
                if (normalized.startsWith(candidateNormalized)) {
                    return true;
                }
            } else if (normalized.equals(candidateNormalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean isOneDrivePath() {
        return realRoots().stream()
                .filter(Objects::nonNull)
                .anyMatch(path -> path.toAbsolutePath().normalize().toString()
                        .toLowerCase().contains("onedrive"));
    }

    public static MonitorRequest fromTreeOrPath(FileTree tree) {
        if (tree.ref() instanceof RealFile file) {
            return recursive(file.path());
        }
        return predeterminedTree(tree);
    }
}
