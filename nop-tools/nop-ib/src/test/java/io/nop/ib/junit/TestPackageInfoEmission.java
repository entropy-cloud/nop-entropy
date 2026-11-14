package io.nop.ib.junit;

import io.nop.javac.jdk.JavaCompileOptions;
import io.nop.javac.jdk.JavaFileCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-C/spring-batch parity defect: nop-javac file compilation did not emit
 * package-info.class for ANNOTATED package-info sources (e.g. JSpecify
 * @NullMarked) — mvn does, so fast-path products diverged from Maven's.
 * Plain javac emits the package-info class file regardless of -proc mode;
 * the same must hold here.
 */
class TestPackageInfoEmission {

    private static final String ANNOTATION_SRC = """
            package pkg;

            import java.lang.annotation.*;

            @Retention(RetentionPolicy.CLASS)
            @Target({ElementType.PACKAGE, ElementType.TYPE_USE})
            public @interface PackageMarker {
            }
            """;

    private static final String PACKAGE_INFO_SRC = """
            @PackageMarker
            package pkg;
            """;

    @Test
    void annotatedPackageInfoProducesClassFile(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("src/pkg");
        Files.createDirectories(src);
        Files.writeString(src.resolve("PackageMarker.java"), ANNOTATION_SRC);
        Files.writeString(src.resolve("package-info.java"), PACKAGE_INFO_SRC);
        Path out = tmp.resolve("classes");
        Files.createDirectories(out);

        JavaFileCompileResult result = new JdkJavaCompiler().compileFiles(
                List.of(src.resolve("PackageMarker.java").toFile(),
                        src.resolve("package-info.java").toFile()),
                new JavaCompileOptions()
                        .setOutputDir(out.toFile())
                        .setClassPaths(List.of())
                        .setImplicitPolicy(JavaCompileOptions.IMPLICIT_NONE)
                        .setEncoding("UTF-8")
                        .setExtraOptions(List.of("-g")));

        assertTrue(result.isSuccess(), String.valueOf(result.getDiagnostics()));
        assertTrue(Files.exists(out.resolve("pkg/PackageMarker.class")), "regular class missing");
        assertTrue(Files.exists(out.resolve("pkg/package-info.class")),
                "annotated package-info.class not emitted (mvn parity defect)");
    }
}
