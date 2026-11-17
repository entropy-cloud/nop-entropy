package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonOut;
import io.nop.ib.lock.BuildLock;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code nop-ib clean}: fast-path product invalidation with mvn-clean parity
 * for the modules it covers (wave 11). Deletes each selected module's target
 * output directories, drops the unit index and the opt-in test-cache entry —
 * a cleaned module must not serve a cached green or an index-derived selection
 * afterwards (I2: products missing → state invalid).
 *
 * <p>Lock discipline: deleting shared products takes {@link BuildLock} (same
 * file as mvnq) — this command is exactly the writer the lock exists for.
 * Requires a fresh model (modules are enumerated from it); a stale model is an
 * explicit usage error pointing at {@code nop-ib model refresh}, never a
 * partial clean.</p>
 */
public final class CleanCommand {
    private CleanCommand() {
    }

    public static int clean(IbEngine engine, String plModules, boolean json,
                            java.io.PrintStream out, java.io.PrintStream err) {
        IbModel model = engine.model();
        String stale = engine.stalenessReason();
        if (stale != null) {
            err.println("usage error: model is stale (" + stale
                    + "); run `nop-ib model refresh` before clean — refusing a partial clean");
            return Commands.EXIT_USAGE;
        }

        Set<String> moduleIds = new LinkedHashSet<>();
        if (plModules != null && !plModules.isBlank()) {
            for (String raw : plModules.split(",")) {
                String id = raw.trim();
                if (id.startsWith(":"))
                    id = id.substring(1);
                if (model.module(id) == null) {
                    err.println("usage error: -pl module not in reactor: " + id);
                    return Commands.EXIT_USAGE;
                }
                moduleIds.add(id);
            }
        } else {
            for (IbModule m : model.getModules())
                moduleIds.add(m.getArtifactId());
        }

        long freed = 0;
        List<String> cleaned = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        try (BuildLock lock = BuildLock.acquire(engine.getRepoRoot())) {
            for (String moduleId : moduleIds) {
                IbModule module = model.module(moduleId);
                try {
                    long bytes = deleteTree(model.resolve(module.getOutputDir()).toPath());
                    bytes += deleteTree(model.resolve(module.getTestOutputDir()).toPath());
                    engine.getUnitStore().delete(moduleId);
                    cleaned.add(moduleId);
                    freed += bytes;
                } catch (Exception e) {
                    err.println("clean failed for " + moduleId + ": " + e);
                    failed.add(moduleId);
                }
            }
            // drop the test-cache entries of cleaned modules (fingerprint would
            // be null anyway after index deletion, but dropping keeps the file honest)
            TestCacheStore cache = new TestCacheStore(engine);
            int dropped = cache.dropModules(cleaned);
            cache.save();
            if (!json) {
                out.println("cleaned " + cleaned.size() + " module(s), freed "
                        + (freed / 1024) + " KiB, test-cache entries dropped: " + dropped);
                for (String id : cleaned)
                    out.println("  - " + id);
            } else {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", failed.isEmpty());
                m.put("cleaned", cleaned);
                m.put("failed", failed);
                m.put("freedBytes", freed);
                m.put("testCacheEntriesDropped", dropped);
                out.println(JsonOut.write(m));
            }
        } catch (Exception e) {
            err.println("clean failed: " + e);
            return Commands.EXIT_FAILED;
        }
        return failed.isEmpty() ? Commands.EXIT_OK : Commands.EXIT_FAILED;
    }

    /** Deletes a directory tree; missing dir = 0 bytes, not an error. */
    private static long deleteTree(Path root) throws IOException {
        if (!Files.isDirectory(root))
            return 0;
        final long[] size = {0};
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    if (Files.isRegularFile(p))
                        size[0] += Files.size(p);
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
        return size[0];
    }
}
