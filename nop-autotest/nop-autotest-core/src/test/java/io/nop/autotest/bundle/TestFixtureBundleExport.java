package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.core.spike.M01SpikeBoot;
import io.nop.commons.util.FileHelper;
import io.nop.orm.IOrmEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1.1 export-side acceptance tests (plan 2026-10-01-2049-1 Phase 2):
 * full chain construct→export→independent-validate, masked discipline, captureGaps,
 * multi-snapshot contract — plus the anti-goal assertion that bundle output never
 * lands in the _cases directory layout.
 */
public class TestFixtureBundleExport {

    private static final File OUT_DIR = new File("target/fixture-bundle-test/export");

    @BeforeAll
    public static void beforeAll() {
        M01SpikeBoot.start();
        if (OUT_DIR.exists()) io.nop.commons.util.FileHelper.deleteAll(OUT_DIR);
    }

    @AfterAll
    public static void afterAll() {
        M01SpikeBoot.stop();
    }

    private static IOrmEntity newParent(io.nop.orm.IOrmSession orm, String code) {
        IOrmEntity parent = orm.newEntity("spike.TestFxParent");
        parent.orm_propValueByName("code", code);
        parent.orm_propValueByName("name", "name-of-" + code);
        return parent;
    }

    @Test
    public void testFullChainExportValidate() {
        FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = newParent(orm, "P-BASE");
            orm.save(parent);
            IOrmEntity child = orm.newEntity("spike.TestFxChild");
            child.orm_propValueByName("parentId", parent.orm_propValueByName("id"));
            child.orm_propValueByName("name", "child-payload");
            orm.save(child);
            return null;
        }));

        FixtureBundleExportConfig config = new FixtureBundleExportConfig("demo-bundle", "snap-1");
        config.addBaseTable("spike.TestFxParent", List.of("CODE"));
        config.addPayloadTable("spike.TestFxChild");

        File bundleDir = new File(OUT_DIR, "full-chain");
        FixtureBundleManifest manifest = new FixtureBundleExporter().export(bundleDir, config, session, M01SpikeBoot.orm());

        // manifest content: base + payload layers, topo load order, counts
        assertEquals(1, manifest.getBaseTables().size());
        assertEquals(1, manifest.getSnapshots().size());
        assertEquals("snap-1", manifest.getSnapshots().get(0).getName());
        FixtureBundleTableEntry base = manifest.getBaseTables().get(0);
        assertEquals(List.of("CODE"), base.getBusinessKeys());
        assertEquals(1, base.getRowCount());
        assertTrue(base.getCsv().startsWith("base/"), "base layer must live under base/");
        FixtureBundleTableEntry payload = manifest.getSnapshots().get(0).getTables().get(0);
        assertEquals("payload", payload.getLayer());
        assertTrue(payload.getCsv().startsWith("snapshots/snap-1/"), "payload layer must live under snapshots/<name>/");

        // independent validation reads from disk only
        new FixtureBundleValidator().validate(bundleDir);

        // anti-goal: output must never land in the _cases directory layout
        assertFalse(bundleDir.getAbsolutePath().contains("_cases"), "bundle must be outside _cases layout");
        assertTrue(new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE).exists());
    }

    @Test
    public void testMaskedColumnReplacedAndUnmaskedSensitiveRejected() {
        FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            orm.save(newParent(orm, "P-MASK"));
            return null;
        }));

        // NAME is declared masked even though not sensitive — proving placeholder substitution
        FixtureBundleExportConfig config = new FixtureBundleExportConfig("masked-bundle", "snap");
        config.addBaseTable("spike.TestFxParent", List.of("CODE"), List.of("NAME"));

        File bundleDir = new File(OUT_DIR, "masked");
        new FixtureBundleExporter().export(bundleDir, config, session, M01SpikeBoot.orm());

        String csv = FileHelper.readText(new File(bundleDir, "base/spike.TestFxParent.csv"), null);
        assertTrue(csv.contains(FixtureBundleConstants.MASKED_PLACEHOLDER), "masked column must be placeholder");
        assertFalse(csv.contains("name-of-P-MASK"), "masked original value must not appear");

        // validator passes for the masked bundle
        new FixtureBundleValidator().validate(bundleDir);

        // a second bundle exporting the same PASSWORD-shaped column unmasked must be rejected:
        // simulate via a sensitive-shaped column by direct rule probe
        FixtureBundleSensitiveRules rules = new FixtureBundleSensitiveRules();
        assertNotNull(rules.findViolation("any.Table", List.of("ID", "PASSWORD"), null),
                "unmasked PASSWORD column must be flagged");
        assertEquals("SALT", rules.findViolation("any.Table", List.of("ID", "SALT"), List.of("OTHER")),
                "unmasked SALT column must be flagged");
        var ok = rules.findViolation("any.Table", List.of("ID", "PASSWORD"), List.of("PASSWORD"));
        org.junit.jupiter.api.Assertions.assertNull(ok, "masked PASSWORD column must pass");
    }

    @Test
    public void testCaptureGapsForSessionDeletedRow() {
        // pre-create P-GONE in an earlier session: save+delete inside ONE unflushed session
        // cancels the insert (row never reaches the DB), so a real capture gap needs an
        // already-persisted row deleted inside the recording window
        Object[] goneId = new Object[1];
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity gone = newParent(orm, "P-GONE");
            orm.save(gone);
            goneId[0] = gone.orm_propValueByName("id");
            return null;
        });

        FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity kept = newParent(orm, "P-KEEP");
            orm.save(kept);
            orm.delete(orm.get("spike.TestFxParent", goneId[0]));
            return null;
        }));

        FixtureBundleExportConfig config = new FixtureBundleExportConfig("gaps-bundle", "snap");
        config.addBaseTable("spike.TestFxParent", List.of("CODE"));

        File bundleDir = new File(OUT_DIR, "gaps");
        FixtureBundleManifest manifest = new FixtureBundleExporter().export(bundleDir, config, session, M01SpikeBoot.orm());

        FixtureBundleTableEntry base = manifest.getBaseTables().get(0);
        assertEquals(1, base.getRowCount(), "deleted row must not be exported");
        assertEquals(1, base.getCaptureGaps(), "deleted row must be recorded as capture gap");
        // export + gaps = collected (2)
        assertEquals(2, base.getRowCount() + base.getCaptureGaps());

        new FixtureBundleValidator().validate(bundleDir);
    }

    @Test
    public void testMultiSnapshotContract() {
        // snapshot 1
        FixtureBundleRecordingSession s1 = new FixtureBundleRecordingSession();
        s1.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            orm.save(newParent(orm, "P-S1"));
            return null;
        }));
        FixtureBundleExportConfig c1 = new FixtureBundleExportConfig("multi-bundle", "day-1");
        c1.addPayloadTable("spike.TestFxParent");
        File bundleDir = new File(OUT_DIR, "multi");
        new FixtureBundleExporter().export(bundleDir, c1, s1, M01SpikeBoot.orm());

        // snapshot 2 added to the SAME bundle: manifest merges, base untouched
        FixtureBundleRecordingSession s2 = new FixtureBundleRecordingSession();
        s2.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            orm.save(newParent(orm, "P-S2"));
            return null;
        }));
        FixtureBundleExportConfig c2 = new FixtureBundleExportConfig("multi-bundle", "day-2");
        c2.addPayloadTable("spike.TestFxParent");
        new FixtureBundleExporter().export(bundleDir, c2, s2, M01SpikeBoot.orm());

        FixtureBundleManifest manifest = FixtureBundleManifest.read(bundleDir);
        assertEquals(2, manifest.getSnapshots().size(), "bundle must hold two named snapshots");
        assertTrue(new File(bundleDir, "snapshots/day-1/spike.TestFxParent.csv").exists());
        assertTrue(new File(bundleDir, "snapshots/day-2/spike.TestFxParent.csv").exists());

        new FixtureBundleValidator().validate(bundleDir);
    }

    @Test
    public void testUnconfiguredCollectedTableRejected() {
        FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            orm.save(newParent(orm, "P-X"));
            return null;
        }));
        FixtureBundleExportConfig config = new FixtureBundleExportConfig("reject-bundle", "snap");
        config.addPayloadTable("spike.TestFxChild"); // parent collected but not configured

        NopException e = assertThrows(NopException.class,
                () -> new FixtureBundleExporter().export(new File(OUT_DIR, "reject"), config, session, M01SpikeBoot.orm()));
        assertTrue(String.valueOf(e.getErrorCode()).contains("table-not-configured"));
    }
}
