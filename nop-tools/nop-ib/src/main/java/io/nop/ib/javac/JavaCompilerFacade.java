package io.nop.ib.javac;

import io.nop.javac.jdk.JavaCompileOptions;
import io.nop.javac.jdk.JavaDiagnostic;
import io.nop.javac.jdk.JavaFileCompileResult;
import io.nop.javac.jdk.JdkJavaCompiler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Option assembly for fast-path compilations (design 05 IB-D5): nop-javac file
 * compilation with -implicit:none (explicit: no hidden product writes), default
 * -proc:none (L2 modules never reach this path), per-execution --release from the
 * model. Structure follows the model's own facts; no hardcoded release.
 */
public final class JavaCompilerFacade {
    private JavaCompilerFacade() {
    }

    public record CompileResult(boolean success, List<String> diagnostics) {
    }

    public static CompileResult compile(List<File> sourceFiles, Path outputDir,
                                        List<String> classpathEntries, String release,
                                        List<File> sourcePath) {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        JavaCompileOptions options = new JavaCompileOptions()
                .setOutputDir(outputDir.toFile())
                .setClassPaths(classpathEntries)
                .setImplicitPolicy(JavaCompileOptions.IMPLICIT_NONE)
                .setProcMode("none")
                .setEncoding(StandardCharsets.UTF_8.name());
        if (release != null && !release.isBlank())
            options.setRelease(release);
        if (sourcePath != null && !sourcePath.isEmpty())
            options.setSourcePath(sourcePath);

        JavaFileCompileResult result = compiler.compileFiles(sourceFiles, options);
        List<String> diags = new ArrayList<>();
        for (JavaDiagnostic d : result.getDiagnostics()) {
            diags.add(d.toString());
        }
        return new CompileResult(result.isSuccess(), diags);
    }
}
