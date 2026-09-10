package io.nop.treesitter.bench;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Materializes the benchmark fixtures for the C reference harness. Invoked by
 * bench/run-c-reference.sh via a plain {@code java -cp} launch (the repo pins
 * exec-maven-plugin's mainClass to the codegen task).
 */
public class BenchSourcesDump {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "_tmp/ts-bench/inputs");
        Files.createDirectories(dir);
        write(dir, "json-10k.json", BenchSources.jsonSource(10 * 1024));
        write(dir, "json-100k.json", BenchSources.jsonSource(100 * 1024));
        write(dir, "json-1m.json", BenchSources.jsonSource(1024 * 1024));
        write(dir, "java-single.java", BenchSources.javaSingleFile());
        byte[][] project = BenchSources.javaProjectFiles();
        for (int i = 0; i < project.length; i++) {
            write(dir.resolve("project"), "ProjectClass" + i + ".java", project[i]);
        }
        System.out.println("dumped to " + dir.toAbsolutePath());
    }

    private static void write(Path dir, String name, byte[] bytes) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), bytes);
    }
}
