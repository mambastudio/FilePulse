package com.mamba.filepulse.model;

import com.mamba.filepulse.file.FileRef;
import com.mamba.filepulse.file.RealFile;
import com.mamba.filepulse.file.VirtualFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class FileTree {
    private final FileRef ref;
    private final List<FileTree> children;

    public FileTree(FileRef ref) {
        this(ref, new ArrayList<>());
    }

    public FileTree(FileRef ref, List<FileTree> children) {
        this.ref = Objects.requireNonNull(ref, "ref must not be null");
        this.children = new ArrayList<>(Objects.requireNonNull(children, "children must not be null"));
    }

    public static FileTree virtualRoot(String name, List<Path> roots) {
        FileTree root = new FileTree(new VirtualFile(name));
        roots.stream().map(FileTree::scan).forEach(root.children::add);
        return root;
    }

    public static FileTree scan(Path root) {
        return scan(root, Integer.MAX_VALUE);
    }

    public static FileTree scanShallow(Path root) {
        return scan(root, 1);
    }

    public static FileTree scan(Path root, int depth) {
        Objects.requireNonNull(root, "root must not be null");
        FileTree tree = new FileTree(new RealFile(root));
        if (depth <= 0 || !Files.isDirectory(root)) {
            return tree;
        }

        try (Stream<Path> stream = Files.list(root)) {
            stream.map(path -> scan(path, depth - 1)).forEach(tree.children::add);
        } catch (IOException ignored) {
            // Unreadable directories still represent monitorable roots for native watchers.
        }
        return tree;
    }

    public FileRef ref() {
        return ref;
    }

    public List<FileTree> children() {
        return children;
    }

    public Optional<Path> path() {
        return ref instanceof RealFile file
                ? Optional.of(file.path().toAbsolutePath().normalize())
                : Optional.empty();
    }

    public boolean isDirectory() {
        return ref instanceof RealFile file && file.isDirectory();
    }

    public boolean isTerminal() {
        return ref instanceof RealFile file && file.isLeaf();
    }

    public List<Path> directories() {
        List<Path> directories = new ArrayList<>();
        collectDirectories(directories);
        return directories;
    }

    public List<Path> realRoots() {
        if (ref instanceof RealFile file) {
            return List.of(file.path());
        }

        List<Path> roots = new ArrayList<>();
        for (FileTree child : children) {
            if (child.ref instanceof RealFile file) {
                roots.add(file.path());
            } else {
                roots.addAll(child.realRoots());
            }
        }
        return roots;
    }

    public Optional<FileTree> find(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (path().filter(normalized::equals).isPresent()) {
            return Optional.of(this);
        }

        for (FileTree child : children) {
            Optional<FileTree> found = child.find(path);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public boolean reload(Path path, int depth) {
        Path normalized = path.toAbsolutePath().normalize();
        if (path().filter(normalized::equals).isPresent()) {
            FileTree fresh = scan(normalized, depth);
            children.clear();
            children.addAll(fresh.children());
            return true;
        }

        for (FileTree child : children) {
            if (child.reload(path, depth)) {
                return true;
            }
        }
        return false;
    }

    private void collectDirectories(List<Path> directories) {
        if (ref instanceof RealFile file && file.isDirectory()) {
            directories.add(file.path());
        }
        for (FileTree child : children) {
            child.collectDirectories(directories);
        }
    }

    @Override
    public String toString() {
        return ref.name();
    }
}
