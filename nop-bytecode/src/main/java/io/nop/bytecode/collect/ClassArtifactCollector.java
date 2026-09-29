package io.nop.bytecode.collect;

import io.nop.bytecode.NopBytecodeException;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Collection layer v0: enumerates class artifacts from class directories (Maven reactor
 * {@code target/classes} layout) and jars, with manifest-based incremental collection.
 *
 * <p>Contract (design/nop-bytecode 00-overview §3.1): inputs are prebuilt outputs — this layer
 * never compiles; a missing input is a loud {@link MissingInputException}; an unparseable
 * class file is a loud {@link NopBytecodeException}; unchanged artifacts (path + size + mtime
 * matched against the manifest) are reused without re-reading the file.
 */
public final class ClassArtifactCollector {

    /**
     * Collect all class artifacts from the given inputs. When {@code manifest} is non-null it is
     * used as the delta baseline and updated in place with this run's state (the caller persists
     * it via {@link CollectManifest#save}).
     */
    public CollectResult collect(List<CollectInput> inputs, CollectManifest manifest) {
        List<String> missing = new ArrayList<>();
        for (CollectInput input : inputs) {
            if (!Files.exists(input.path())) {
                missing.add(input.toString());
            } else if (input.kind() == CollectInput.Kind.DIRECTORY && !Files.isDirectory(input.path())) {
                missing.add(input + " (not a directory)");
            } else if (input.kind() == CollectInput.Kind.JAR && !Files.isRegularFile(input.path())) {
                missing.add(input + " (not a regular file)");
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingInputException(missing);
        }

        // delta baseline = manifest state BEFORE this run; the manifest object itself is
        // mutated in place during collection (parseAndBuild/collectJar write through), so
        // classification must read the snapshot, not the live map.
        Map<String, CollectManifest.Entry> baseline = manifest == null ? null : manifest.snapshot();
        List<ClassArtifact> artifacts = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        for (CollectInput input : inputs) {
            if (input.kind() == CollectInput.Kind.DIRECTORY) {
                collectDirectory(input, manifest, artifacts, seenPaths);
            } else {
                collectJar(input, manifest, artifacts, seenPaths);
            }
        }
        artifacts.sort(Comparator.comparing(ClassArtifact::relativePath));

        List<ClassArtifact> added = new ArrayList<>();
        List<ClassArtifact> changed = new ArrayList<>();
        int unchanged = 0;
        for (ClassArtifact a : artifacts) {
            CollectManifest.Entry prior = baseline == null ? null : baseline.get(a.relativePath());
            if (prior == null) {
                added.add(a);
            } else if (!prior.sha256.equals(a.sha256())) {
                changed.add(a);
            } else {
                unchanged++;
            }
        }
        List<String> removed = new ArrayList<>();
        if (baseline != null) {
            for (String path : baseline.keySet()) {
                if (!seenPaths.contains(path)) {
                    removed.add(path);
                    manifest.remove(path);
                }
            }
            removed.sort(Comparator.naturalOrder());
        }
        return new CollectResult(artifacts, added, changed, removed, unchanged);
    }

    private void collectDirectory(CollectInput input, CollectManifest manifest,
                                  List<ClassArtifact> out, Set<String> seenPaths) {
        Path root = input.path();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> {
                        String relative = root.relativize(p).toString().replace('\\', '/');
                        String artifactPath = relative;
                        seenPaths.add(artifactPath);
                        out.add(resolveArtifact(p, artifactPath, () -> mtimeOf(p), manifest));
                    });
        } catch (IOException e) {
            throw new NopBytecodeException("Failed to walk class directory " + root, e);
        }
    }

    private void collectJar(CollectInput input, CollectManifest manifest,
                            List<ClassArtifact> out, Set<String> seenPaths) {
        Path jarPath = input.path();
        long jarMtime = mtimeOf(jarPath);
        String jarName = jarPath.getFileName() == null ? jarPath.toString() : jarPath.getFileName().toString();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String artifactPath = jarName + "!" + entry.getName();
                seenPaths.add(artifactPath);
                long entrySize = entry.getSize();
                // jar-level mtime: any jar rewrite bumps all its entries
                String manifestPath = artifactPath;
                if (manifest != null) {
                    CollectManifest.Entry prior = manifest.get(manifestPath);
                    if (prior != null && prior.size == entrySize && prior.mtimeMillis == jarMtime
                            && prior.sha256 != null) {
                        out.add(new ClassArtifact(artifactPath, prior.className, prior.majorVersion,
                                prior.size, prior.sha256));
                        continue;
                    }
                }
                byte[] bytes;
                try (InputStream in = jar.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                out.add(parseAndBuild(artifactPath, bytes, jarMtime, manifest));
            }
        } catch (IOException e) {
            throw new NopBytecodeException("Failed to read jar " + jarPath, e);
        }
    }

    private ClassArtifact resolveArtifact(Path file, String artifactPath, MtimeSupplier mtime,
                                          CollectManifest manifest) {
        try {
            long size = Files.size(file);
            if (manifest != null) {
                CollectManifest.Entry prior = manifest.get(artifactPath);
                if (prior != null && prior.size == size && prior.mtimeMillis == mtime.get()
                        && prior.sha256 != null) {
                    return new ClassArtifact(artifactPath, prior.className, prior.majorVersion,
                            prior.size, prior.sha256);
                }
            }
            byte[] bytes = Files.readAllBytes(file);
            return parseAndBuild(artifactPath, bytes, mtime.get(), manifest);
        } catch (IOException e) {
            throw new NopBytecodeException("Failed to read class file " + file, e);
        }
    }

    private ClassArtifact parseAndBuild(String artifactPath, byte[] bytes, long mtimeMillis,
                                        CollectManifest manifest) {
        if (bytes.length < 8 || ((bytes[0] & 0xFF) != 0xCA || (bytes[1] & 0xFF) != 0xFE
                || (bytes[2] & 0xFF) != 0xBA || (bytes[3] & 0xFF) != 0xBE)) {
            throw new NopBytecodeException("Not a class file (bad magic): " + artifactPath);
        }
        int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        String className;
        try {
            className = new ClassReader(bytes).getClassName().replace('/', '.');
        } catch (Exception e) {
            throw new NopBytecodeException("Unparseable class file: " + artifactPath, e);
        }
        String sha256 = sha256Hex(bytes);
        ClassArtifact artifact = new ClassArtifact(artifactPath, className, major, bytes.length, sha256);
        if (manifest != null) {
            manifest.put(artifactPath,
                    new CollectManifest.Entry(bytes.length, mtimeMillis, sha256, className, major));
        }
        return artifact;
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : digest.digest(bytes)) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new NopBytecodeException("SHA-256 not available", e);
        }
    }

    private long mtimeOf(Path p) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            return attrs.lastModifiedTime().toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    interface MtimeSupplier {
        long get();
    }
}
