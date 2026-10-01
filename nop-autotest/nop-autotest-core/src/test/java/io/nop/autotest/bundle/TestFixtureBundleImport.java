package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.core.spike.M01SpikeBoot;
import io.nop.orm.model.IColumnModel;
import io.nop.commons.util.FileHelper;
import io.nop.orm.IOrmEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static io.nop.autotest.bundle.FixtureBundleImportErrors.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1.2 import-side acceptance tests (plan 2026-10-01-2142-1 Phase 2): clean replay
 * with id remap + FK rewrite + dangling-ref rejection, dirty-target base
 * non-overwrite, requires-missing, schema drift fail-fast/tolerant, in-run idempotent
 * re-import + UK-conflict wrapping + logical-deleted rejection + sys-table rejection +
 * version-strip normalization (approval condition B1).
 */
public class TestFixtureBundleImport {

    private static final File OUT_DIR = new File("target/fixture-bundle-test/import");

    @BeforeAll
    public static void beforeAll() throws IOException {
        M01SpikeBoot.start();
        if (OUT_DIR.exists())
            FileHelper.deleteAll(OUT_DIR);
        Files.createDirectories(OUT_DIR.toPath());
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

    /** construct parent+child, export, return bundle dir; ids[0]=parent id, ids[1]=child id */
    private File exportParentChildBundle(String dirName, String code, Object[] ids) {
        FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = newParent(orm, code);
            orm.save(parent);
            IOrmEntity child = orm.newEntity("spike.TestFxChild");
            child.orm_propValueByName("parentId", parent.orm_propValueByName("id"));
            child.orm_propValueByName("name", "child-of-" + code);
            orm.save(child);
            ids[0] = parent.orm_propValueByName("id");
            ids[1] = child.orm_propValueByName("id");
            return null;
        }));

        FixtureBundleExportConfig config = new FixtureBundleExportConfig("replay-" + code, "snap");
        config.addBaseTable("spike.TestFxParent", List.of("CODE"));
        config.addPayloadTable("spike.TestFxChild");

        File bundleDir = new File(OUT_DIR, dirName);
        new FixtureBundleExporter().export(bundleDir, config, session, M01SpikeBoot.orm());
        return bundleDir;
    }

    private static void deleteRow(String entityName, Object id) {
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity entity = orm.get(entityName, id);
            if (entity != null)
                orm.delete(entity);
            return null;
        });
    }

    @Test
    public void testCleanReplayWithIdRemapAndFkRewrite() {
        Object[] ids = new Object[2];
        File bundleDir = exportParentChildBundle("replay", "P-REPLAY", ids);

        // clean the target: the constructed rows must not exist at import time
        deleteRow("spike.TestFxChild", ids[1]);
        deleteRow("spike.TestFxParent", ids[0]);

        FixtureBundleImportResult result = new FixtureBundleImporter().importBundle(bundleDir, M01SpikeBoot.orm());
        assertEquals(1, result.getBaseImported());
        assertEquals(1, result.getPayloadImported());

        // replayed ids are brand-new and different from the source ids
        Object newParentId = result.getMaxNewIds().get("spike.TestFxParent");
        assertNotNull(newParentId);
        assertNotEquals(ids[0], newParentId, "payload/base replay must generate fresh ids");

        // the child's FK was rewritten to the new parent id (to-one back-reference reads through)
        Object childId = result.getMaxNewIds().get("spike.TestFxChild");
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity child = orm.get("spike.TestFxChild", childId);
            assertNotNull(child);
            assertEquals(newParentId, child.orm_propValueByName("parentId"), "FK must be rewritten to the new parent id");
            return null;
        });

        // Decision ③ assertion口径 (iii): a subsequently generated id does not collide
        Object nextId = M01SpikeBoot.orm().runInSession(orm -> orm.save(newParent(orm, "P-AFTER-REPLAY")));
        assertNotEquals(nextId, newParentId);
        assertNotEquals(nextId, childId);
    }

    @Test
    public void testDanglingRefRejected() {
        Object[] ids = new Object[2];
        File bundleDir = exportParentChildBundle("dangling", "P-DANGLE", ids);
        deleteRow("spike.TestFxChild", ids[1]);
        deleteRow("spike.TestFxParent", ids[0]);

        // sever the mapping: drop the base table entry from the manifest so the payload's
        // FK can resolve neither through the package mapping nor the target
        File manifestFile = new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE);
        String text = FileHelper.readText(manifestFile, null);
        int baseStart = text.indexOf("\"baseTables\"");
        int snapStart = text.indexOf("\"snapshots\"");
        assertTrue(baseStart > 0 && snapStart > baseStart);
        text = text.substring(0, baseStart) + "\"baseTables\": [], " + text.substring(snapStart);
        FileHelper.writeText(manifestFile, text, null);

        NopException e = assertThrows(NopException.class,
                () -> new FixtureBundleImporter().importBundle(bundleDir, M01SpikeBoot.orm()));
        assertEquals(ERR_FIXTURE_BUNDLE_DANGLING_REF.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testDirtyTargetBaseNotOverwritten() {
        // pre-existing row owns the business key
        Object existingId = M01SpikeBoot.orm().runInSession(orm -> orm.save(newParent(orm, "P-DIRTY")));

        // construct ANOTHER row with the same business key but a different name, export it
        FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = orm.newEntity("spike.TestFxParent");
            parent.orm_propValueByName("code", "P-DIRTY");
            parent.orm_propValueByName("name", "from-export");
            orm.save(parent);
            return null;
        }));
        FixtureBundleExportConfig config = new FixtureBundleExportConfig("dirty", "snap");
        config.addBaseTable("spike.TestFxParent", List.of("CODE"));
        File bundleDir = new File(OUT_DIR, "dirty");
        new FixtureBundleExporter().export(bundleDir, config, session, M01SpikeBoot.orm());

        // remove the constructed row so the import reconciles against the ORIGINAL row
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity example = orm.newEntity("spike.TestFxParent");
            example.orm_propValueByName("name", "from-export");
            IOrmEntity constructed = orm.findFirstByExample(example);
            if (constructed != null)
                orm.delete(constructed);
            return null;
        });

        FixtureBundleImportResult result = new FixtureBundleImporter().importBundle(bundleDir, M01SpikeBoot.orm());
        assertEquals(0, result.getBaseImported(), "reconciliation must not insert a duplicate");
        assertEquals(1, result.getBaseSkipped());

        // the original row is untouched
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity original = orm.get("spike.TestFxParent", existingId);
            assertNotNull(original);
            assertEquals("name-of-P-DIRTY", original.orm_propValueByName("name"),
                    "base reconciliation must never overwrite the existing row");
            return null;
        });
    }

    @Test
    public void testRequiresMissing() {
        Object[] ids = new Object[2];
        File bundleDir = exportParentChildBundle("requires", "P-REQ", ids);
        deleteRow("spike.TestFxChild", ids[1]);
        deleteRow("spike.TestFxParent", ids[0]);

        // declare an unsatisfiable dependency
        File manifestFile = new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE);
        String text = FileHelper.readText(manifestFile, null);
        if (text.contains("\"requires\": null")) {
            text = text.replace("\"requires\": null", "\"requires\": [\"ghost-bundle\"]");
        } else {
            text = text.replace("\"bundleName\":", "\"requires\": [\"ghost-bundle\"], \"bundleName\":");
        }
        FileHelper.writeText(manifestFile, text, null);

        NopException e = assertThrows(NopException.class,
                () -> new FixtureBundleImporter().importBundle(bundleDir, M01SpikeBoot.orm()));
        assertEquals(ERR_FIXTURE_BUNDLE_REQUIRES_MISSING.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSchemaDriftFailFastAndTolerant() throws IOException {
        Object[] ids = new Object[2];
        File bundleDir = exportParentChildBundle("drift", "P-DRIFT", ids);
        deleteRow("spike.TestFxChild", ids[1]);
        deleteRow("spike.TestFxParent", ids[0]);

        // tamper the fingerprint
        File manifestFile = new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE);
        String text = FileHelper.readText(manifestFile, null);
        String real = text.substring(text.indexOf("\"columnFingerprint\": \"") + "\"columnFingerprint\": \"".length());
        String tampered = text.replace(real.substring(0, 16), "deadbeefdeadbeef");
        FileHelper.writeText(manifestFile, tampered, null);

        NopException e = assertThrows(NopException.class,
                () -> new FixtureBundleImporter().importBundle(bundleDir, M01SpikeBoot.orm()));
        assertEquals(ERR_FIXTURE_BUNDLE_SCHEMA_DRIFT.getErrorCode(), e.getErrorCode());

        // tolerant mode: warning registered, import proceeds
        FixtureBundleImportResult result = new FixtureBundleImporter()
                .importBundle(bundleDir, M01SpikeBoot.orm(), null, true);
        assertFalse(result.getWarnings().isEmpty(), "tolerant mode must register the drift warning");
        assertEquals(1, result.getBaseImported());
    }

    @Test
    public void testInRunIdempotencyUkConflictAndGuards() throws IOException {
        // --- in-run idempotency: second import of the same payload rows skips ---
        Object[] ids = new Object[2];
        File bundleDir = exportParentChildBundle("idem", "P-IDEM", ids);
        deleteRow("spike.TestFxChild", ids[1]);
        deleteRow("spike.TestFxParent", ids[0]);

        FixtureBundleImporter importer = new FixtureBundleImporter();
        importer.importBundle(bundleDir, M01SpikeBoot.orm());
        FixtureBundleImportResult second = importer.importBundle(bundleDir, M01SpikeBoot.orm());
        assertEquals(1, second.getPayloadSkipped(), "same-instance re-import must skip payload rows");

        // --- UK conflict: duplicate business keys within one package ---
        // execution-phase finding: row-by-row reconciliation sees each flushed insert, so
        // a duplicated package business key would be SILENTLY MERGED into the first row —
        // the importer rejects it up front as the named UK-conflict error instead
        try {
            Object[] uids = new Object[2];
            // two DISTINCT constructed rows sharing one business key
            FixtureBundleRecordingSession session = new FixtureBundleRecordingSession();
            session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
                for (String name : new String[]{"one", "two"}) {
                    IOrmEntity parent = orm.newEntity("spike.TestFxParent");
                    parent.orm_propValueByName("code", "P-UK");
                    parent.orm_propValueByName("name", name);
                    orm.save(parent);
                }
                return null;
            }));
            FixtureBundleExportConfig config = new FixtureBundleExportConfig("uk", "snap");
            config.addBaseTable("spike.TestFxParent", List.of("CODE"));
            File ukDir = new File(OUT_DIR, "uk");
            new FixtureBundleExporter().export(ukDir, config, session, M01SpikeBoot.orm());
            // remove both constructed rows so the import inserts from scratch
            M01SpikeBoot.orm().runInSession(orm -> {
                IOrmEntity example = orm.newEntity("spike.TestFxParent");
                example.orm_propValueByName("code", "P-UK");
                for (IOrmEntity entity : orm.findAllByExample(example, null)) {
                    orm.delete(entity);
                }
                return null;
            });

            NopException e = assertThrows(NopException.class,
                    () -> new FixtureBundleImporter().importBundle(ukDir, M01SpikeBoot.orm()));
            assertEquals(ERR_FIXTURE_BUNDLE_UK_CONFLICT.getErrorCode(), e.getErrorCode());
        } finally {
            // no-op slot kept: concurrent-window duplicate-key wrap (flush at txn commit)
            // stays in the importer for real concurrency; deterministic single-thread
            // testing exercises the in-package detection above
        }

        // --- includeLogicalDeleted=true rejected ---
        Object[] gids = new Object[2];
        File gapsDir = exportParentChildBundle("logical", "P-LOGICAL", gids);
        File logicalManifest = new File(gapsDir, FixtureBundleConstants.MANIFEST_FILE);
        String logicalText = FileHelper.readText(logicalManifest, null);
        // the M1.1 exporter already writes includeLogicalDeleted:false — flip the base
        // entry's declaration to true
        logicalText = logicalText.replace("\"includeLogicalDeleted\": false",
                "\"includeLogicalDeleted\": true");
        FileHelper.writeText(logicalManifest, logicalText, null);
        NopException le = assertThrows(NopException.class,
                () -> new FixtureBundleImporter().importBundle(gapsDir, M01SpikeBoot.orm()));
        assertEquals(ERR_FIXTURE_BUNDLE_LOGICAL_DELETED.getErrorCode(), le.getErrorCode());

        // --- sys/sequence table rejected at the entry point ---
        File sysDir = exportParentChildBundle("sys", "P-SYS", new Object[2]);
        File sysManifest = new File(sysDir, FixtureBundleConstants.MANIFEST_FILE);
        String sysText = FileHelper.readText(sysManifest, null);
        sysText = sysText.replace("spike.TestFxParent", "io.nop.sys.dao.entity.NopSysSequence");
        FileHelper.writeText(sysManifest, sysText, null);
        NopException se = assertThrows(NopException.class,
                () -> new FixtureBundleImporter().importBundle(sysDir, M01SpikeBoot.orm()));
        assertEquals(ERR_FIXTURE_BUNDLE_SYS_TABLE_FORBIDDEN.getErrorCode(), se.getErrorCode());
    }

    @Test
    public void testVersionColumnsStrippedAndManifestForwardCompat() throws IOException {
        // B1 normalization assertion (real importer path): non-model columns — packaged
        // VERSION/DEL_VERSION of richer target models — are stripped; PK dropped
        io.nop.orm.model.IEntityModel model = M01SpikeBoot.orm().getSessionFactory().getOrmModel()
                .getEntityModel("spike.TestFxParent");
        java.util.Map<String, IColumnModel> byCode = new java.util.LinkedHashMap<>();
        for (IColumnModel col : model.getColumns())
            byCode.put(col.getCode(), col);
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("ID", 1L);
        row.put("CODE", "C");
        row.put("VERSION", 7);
        row.put("DEL_VERSION", 3L);
        java.util.Map<String, Object> normalized = FixtureBundleImporter.normalizeRow(byCode, row);
        assertFalse(normalized.containsKey("ID"), "PK must be stripped for platform generation");
        assertFalse(normalized.containsKey("VERSION"));
        assertFalse(normalized.containsKey("DEL_VERSION"));
        assertEquals("C", normalized.get("CODE"));

        // approval condition A4 note: typed manifest parse tolerates unknown fields
        Object[] ids = new Object[2];
        File bundleDir = exportParentChildBundle("compat", "P-COMPAT", ids);
        File manifestFile = new File(bundleDir, FixtureBundleConstants.MANIFEST_FILE);
        String text = FileHelper.readText(manifestFile, null);
        text = text.replace("\"formatVersion\": 1,", "\"formatVersion\": 1, \"futureField\": 123,");
        FileHelper.writeText(manifestFile, text, null);
        FixtureBundleManifest manifest = FixtureBundleManifest.read(bundleDir);
        assertEquals("replay-P-COMPAT", manifest.getBundleName());
    }
}
