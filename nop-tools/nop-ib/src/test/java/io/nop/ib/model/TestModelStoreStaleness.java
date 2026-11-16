package io.nop.ib.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave6 6A-1 pins F5/F8: staleness must fire for poms ADDED after the refresh
 * (the old hash-map-only check never saw a brand-new module) and for a JDK
 * toolchain change (recorded but never compared).
 */
class TestModelStoreStaleness {

    private Path repo;
    private ModelStore store;
    private IbModel model;

    private void setup() throws Exception {
        repo = Files.createTempDirectory("ib-stale");
        store = new ModelStore(repo);
        model = new IbModel();
        model.setRepoRoot(repo.toString());
        model.setJdkVersion(System.getProperty("java.version") + ", vendor: test");
        Path pomA = repo.resolve("a/pom.xml");
        Files.createDirectories(pomA.getParent());
        Files.writeString(pomA, "<project/>", StandardCharsets.UTF_8);
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put(repo.relativize(pomA).toString(), ModelStore.sha256(pomA));
        model.getPomHashes().putAll(hashes);
        store.save(model);
    }

    @Test
    void freshModelPasses() throws Exception {
        setup();
        assertNull(store.stalenessReason(model));
    }

    /** F5 pin: a pom added after the refresh must invalidate the model. */
    @Test
    void addedPomIsStale() throws Exception {
        setup();
        Path pomB = repo.resolve("b/pom.xml");
        Files.createDirectories(pomB.getParent());
        Files.writeString(pomB, "<project/>", StandardCharsets.UTF_8);
        String reason = store.stalenessReason(model);
        assertNotNull(reason, "a pom added after refresh must trigger staleness");
        assertTrue(reason.contains("pom added"));
    }

    @Test
    void changedPomIsStale() throws Exception {
        setup();
        Files.writeString(repo.resolve("a/pom.xml"), "<project changed='1'/>", StandardCharsets.UTF_8);
        assertTrue(store.stalenessReason(model).contains("pom changed"));
    }

    /** F8 pin: a recorded-but-never-compared JDK change must invalidate. */
    @Test
    void jdkChangeIsStale() throws Exception {
        setup();
        model.setJdkVersion("99.0.0, vendor: other");
        assertTrue(store.stalenessReason(model).contains("jdk changed"));
    }

    @Test
    void pomRemovedIsStale() throws Exception {
        setup();
        Files.delete(repo.resolve("a/pom.xml"));
        assertTrue(store.stalenessReason(model).contains("pom missing"));
    }
}
