package io.nop.ib.index;

import io.nop.ib.NopIbException;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Persists unit indexes at {@code .nop-ib/units/<module>.bin} (versioned JSON
 * payload; see {@link UnitIndex} format note).
 */
public final class UnitIndexStore {
    public static final String STATE_DIR = ".nop-ib";

    private final Path repoRoot;

    public UnitIndexStore(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    private Path fileFor(String moduleArtifactId) {
        return repoRoot.resolve(STATE_DIR).resolve("units")
                .resolve(moduleArtifactId + ".bin");
    }

    public boolean exists(String moduleArtifactId) {
        return Files.isRegularFile(fileFor(moduleArtifactId));
    }

    public void save(UnitIndex index) {
        Path file = fileFor(index.getModuleArtifactId());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JsonOut.write(index.toMap()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopIbException("failed to write unit index " + file, e);
        }
    }

    public UnitIndex load(String moduleArtifactId) {
        Path file = fileFor(moduleArtifactId);
        if (!Files.isRegularFile(file))
            throw new NopIbException("unit index missing: " + file);
        try {
            UnitIndex index = UnitIndex.fromMap(JsonIn.asMapOrEmpty(JsonIn.parse(
                    Files.readString(file, StandardCharsets.UTF_8))));
            if (!UnitIndex.FORMAT_VERSION.equals(index.getFormatVersion()))
                throw new NopIbException("unit index format mismatch in " + file
                        + ": " + index.getFormatVersion());
            return index;
        } catch (IOException e) {
            throw new NopIbException("failed to read unit index " + file, e);
        }
    }

    public long cacheSizeBytes() {
        Path units = repoRoot.resolve(STATE_DIR).resolve("units");
        if (!Files.isDirectory(units))
            return 0;
        try (Stream<Path> stream = Files.walk(units)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
