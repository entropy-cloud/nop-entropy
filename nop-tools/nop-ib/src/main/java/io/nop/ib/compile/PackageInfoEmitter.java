package io.nop.ib.compile;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Emits the synthetic package-info.class files javac does NOT write for
 * UNANNOTATED package-info.java sources (observed on spring-batch: Maven's
 * error-prone processing rounds write them as a side product, a plain javac
 * invocation does not — so a fast-path output dir misses them). Structure is
 * javac's synthetic form: ACC_INTERFACE|ACC_ABSTRACT|ACC_SYNTHETIC, super
 * Object, single SourceFile attribute.
 *
 * <p>Annotated package-info sources are NOT touched: javac already emits those
 * during its default rounds.
 */
public final class PackageInfoEmitter {

    private PackageInfoEmitter() {
    }

    /** class file major version for a --release value (release 17 → major 61). */
    static int majorVersion(int release) {
        return 44 + release;
    }

    /**
     * for every UNANNOTATED package-info.java under the source root whose class
     * file is missing from the output dir, write the synthetic form. Returns the
     * number of files emitted.
     */
    public static int emitMissing(Path sourceRoot, Path outputDir, int release) {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot))
            return 0;
        int[] emitted = {0};
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(p -> p.getFileName().toString().equals("package-info.java"))
                    .forEach(p -> emitIfNeeded(p, sourceRoot, outputDir, release, emitted));
        } catch (IOException e) {
            // unreadable tree: nothing to emit, compilation itself already succeeded
            return emitted[0];
        }
        return emitted[0];
    }

    private static void emitIfNeeded(Path pkgInfoFile, Path sourceRoot, Path outputDir,
                                     int release, int[] emitted) {
        try {
            if (isAnnotated(pkgInfoFile))
                return; // javac writes those during its default rounds
            String pkg = sourceRoot.relativize(pkgInfoFile.getParent()).toString().replace('\\', '/');
            Path classFile = outputDir.resolve(pkg).resolve("package-info.class");
            if (Files.exists(classFile))
                return;
            String internal = pkg.isEmpty() ? "package-info"
                    : pkg + "/package-info";
            byte[] bytes = syntheticPackageInfo(internal,
                    pkgInfoFile.getFileName().toString(), majorVersion(release));
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, bytes);
            emitted[0]++;
        } catch (IOException | RuntimeException e) {
            // a single synthetic file must never fail a successful compilation;
            // the divergence is recorded by the parity harness instead
        }
    }

    /** annotations precede the package clause in package-info.java. */
    private static boolean isAnnotated(Path pkgInfoFile) {
        try {
            for (String line : Files.readAllLines(pkgInfoFile)) {
                String t = line.trim();
                if (t.startsWith("package "))
                    return false;
                if (t.startsWith("@"))
                    return true;
            }
        } catch (IOException ignored) {
            // unreadable: treat as unannotated
        }
        return false;
    }

    static byte[] syntheticPackageInfo(String internalName, String sourceFile, int majorVersion) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(majorVersion, Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC,
                internalName, null, "java/lang/Object", null);
        cw.visitSource(sourceFile, null);
        cw.visitEnd();
        return cw.toByteArray();
    }
}
