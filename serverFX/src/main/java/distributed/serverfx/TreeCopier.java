package distributed.serverfx;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class TreeCopier extends SimpleFileVisitor<Path> {
    private final Path source;
    private final Path target;

    public TreeCopier(Path source, Path target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        // create directory before copying contents
        Path newDir = target.resolve(source.relativize(dir));
        Files.copy(dir, newDir);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.copy(file, target.resolve(source.relativize(file)));
        return FileVisitResult.CONTINUE;
    }
}
