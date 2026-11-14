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
        return compile(sourceFiles, outputDir, classpathEntries, release, null, sourcePath);
    }

    public static CompileResult compile(List<File> sourceFiles, Path outputDir,
                                        List<String> classpathEntries, String release,
                                        String sourceLevel, List<File> sourcePath) {
        return compile(sourceFiles, outputDir, classpathEntries, release, sourceLevel, null, sourcePath);
    }

    public static CompileResult compile(List<File> sourceFiles, Path outputDir,
                                        List<String> classpathEntries, String release,
                                        String sourceLevel, String procMode, List<File> sourcePath) {
        return compile(sourceFiles, outputDir, classpathEntries, release, sourceLevel, procMode,
                sourcePath, false);
    }

    public static CompileResult compile(List<File> sourceFiles, Path outputDir,
                                        List<String> classpathEntries, String release,
                                        String sourceLevel, String procMode, List<File> sourcePath,
                                        boolean parameters) {
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> extra = new ArrayList<>();
        // Maven compiler plugin default debug=true emits plain -g; required for
        // byte-level product parity with mvn compile (item 17 oracle)
        extra.add("-g");
        // -parameters affects bytecode (MethodParameters attributes) — projects
        // requesting it (config element or compilerArg) must be mirrored exactly
        if (parameters)
            extra.add("-parameters");
        JavaCompileOptions options = new JavaCompileOptions()
                .setOutputDir(outputDir.toFile())
                .setClassPaths(classpathEntries)
                .setImplicitPolicy(JavaCompileOptions.IMPLICIT_NONE)
                // proc channel mirrors the model's effective config: L2 modules (explicit
                // processor paths/proc) never reach the fast path, so for L0/L1 the
                // Maven default semantics (full rounds; emits package-info.class) are
                // safe and required for product parity
                .setProcMode(procMode != null && !procMode.isBlank() ? procMode : null)
                .setEncoding(StandardCharsets.UTF_8.name())
                .setExtraOptions(extra);
        // effective channel: explicit release, else the source level (Maven 4 maps
        // equal source/target to --release, verified against mvn -X option dumps)
        if (release != null && !release.isBlank())
            options.setRelease(release);
        else if (sourceLevel != null && !sourceLevel.isBlank())
            options.setRelease(sourceLevel);
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
