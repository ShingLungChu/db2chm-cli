package com.db2chm.cli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fresh sibling workspace; publish the final directory only on success, without replacement. */
public final class OutputTransaction implements AutoCloseable {
    public final Path destination;
    public final Path workspace;
    private boolean published;
    public OutputTransaction(String output) throws Exception {
        destination = Paths.get(output).toAbsolutePath().normalize();
        if (destination.getParent() == null || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new CliFailure(7, "OUTPUT_EXISTS", "Output directory already exists; choose a new directory.");
        Files.createDirectories(destination.getParent());
        workspace = Files.createTempDirectory(destination.getParent(), ".db2chm-");
    }
    public void publish(AtomicBoolean cancelled) throws Exception {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CliFailure(130, "CANCELLED", "Export was cancelled.");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new CliFailure(7, "OUTPUT_EXISTS", "Output directory was created by another process.");
        Files.move(workspace, destination);
        published = true;
    }
    @Override public void close() throws IOException {
        if (published || !Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(workspace, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException { if (error != null) throw error; Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }
}
