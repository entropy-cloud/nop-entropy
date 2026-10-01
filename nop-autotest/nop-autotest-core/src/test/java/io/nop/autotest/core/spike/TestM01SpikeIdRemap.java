package io.nop.autotest.core.spike;

import io.nop.orm.IOrmEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * M0.1 fixture-bundle spike prototype B driver (nop-app-erp plan 2026-10-01-1853-1).
 * <p>
 * Proves Decision ③(a): inserting a saved-off row with its PK stripped lets the
 * platform generate a new id, the oldId→newId mapping is recordable, and the next
 * generated id does not collide with any imported id. Also carries Decision ⑤'s
 * behavioural observation: a direct ORM-session insert with a DANGLING to-one
 * reference value succeeds — i.e. ObjMetaBasedValidator.validateRefValue is NOT on
 * the direct-insert path (it is only reachable via CrudBizModel write paths,
 * CrudBizModel.java:709/987 → CrudToolProvider.newValidator → ObjMetaBasedValidator:431).
 */
public class TestM01SpikeIdRemap {

    @BeforeAll
    public static void beforeAll() {
        M01SpikeBoot.start();
    }

    @AfterAll
    public static void afterAll() {
        M01SpikeBoot.stop();
    }

    @Test
    public void testStripPkImportGeneratesNewIdAndRecordsMapping() {
        // 1. source side: save a row and capture its full row image (the "export")
        Object[] source = new Object[2];
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = orm.newEntity("spike.TestFxParent");
            parent.orm_propValueByName("code", "SRC-001");
            parent.orm_propValueByName("name", "source-parent");
            orm.save(parent);
            source[0] = parent.orm_propValueByName("id");
            return null;
        });
        Long oldId = (Long) source[0];
        assertNotNull(oldId, "source row must have a platform-generated id");

        // 2. import side: strip PK, let the platform generate, record oldId→newId
        Long newId = M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = orm.newEntity("spike.TestFxParent");
            parent.orm_propValueByName("code", "SRC-001");
            parent.orm_propValueByName("name", "source-parent");
            // pk deliberately left unset — remap strategy
            orm.save(parent);
            return (Long) parent.orm_propValueByName("id");
        });
        assertNotNull(newId);
        assertNotEquals(oldId, newId, "platform must generate a fresh id for the stripped pk");

        // 3. "no conflict on subsequent numbering" assertion:
        //    the next platform-generated id must not collide with any imported id
        Long nextId = M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = orm.newEntity("spike.TestFxParent");
            parent.orm_propValueByName("code", "SRC-002");
            orm.save(parent);
            return (Long) parent.orm_propValueByName("id");
        });
        assertFalse(nextId.equals(oldId) || nextId.equals(newId),
                "subsequent generated id must not collide with imported ids");
    }

    @Test
    public void testDirectInsertWithDanglingToOneSucceeds() {
        // Decision ⑤ behavioural observation: validateRefValue is NOT on the direct-insert
        // path — a dangling to-one (parentId pointing at a non-existent parent) saves fine.
        // Under a CrudBizModel write path this would fail with a ref-value validation error.
        Object[] ids = new Object[1];
        M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity child = orm.newEntity("spike.TestFxChild");
            child.orm_propValueByName("parentId", 999_999_999L);
            child.orm_propValueByName("name", "dangling-ref-child");
            orm.save(child);
            ids[0] = child.orm_propValueByName("id");
            return null;
        });
        assertNotNull(ids[0]);

        // re-read in a fresh session — proving the dangling-ref row is really persisted
        IOrmEntity loaded = M01SpikeBoot.orm().runInSession(orm ->
                orm.get("spike.TestFxChild", ids[0]));
        assertNotNull(loaded, "dangling-ref row must be persisted by the direct insert");
        assertEquals(999_999_999L, loaded.orm_propValueByName("parentId"));
    }
}
