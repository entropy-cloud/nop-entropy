package io.nop.bytecode;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Test infrastructure shared by kernel tests: compile fixture sources with the JDK compiler. */
public final class TestCompiler {
    private TestCompiler() { }

    public static void compile(String release, Path outDir, String fileName, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "system java compiler available");
        Path srcDir = outDir.resolveSibling(outDir.getFileName() + "-src");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve(fileName), source.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(outDir);
        int rc = compiler.run(null, null, null,
                "--release", release, "-d", outDir.toString(), srcDir.resolve(fileName).toString());
        assertEquals(0, rc, "fixture compilation failed for " + fileName);
    }
}
