package io.nop.autotest.core.spike;

import io.nop.autotest.core.execute.EntityRow;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0.1 fixture-bundle spike prototype A driver (nop-app-erp plan 2026-10-01-1853-1).
 * <p>
 * Proves: a non-test class can open a recording session (hook register/unregister)
 * on the shared session factory, construction saves are collected, rows are
 * extractable via hook.getDataMap(), and sessions are isolated (each hook only
 * collects rows touched inside its own window).
 */
public class TestM01SpikeRecordingSession {

    @BeforeAll
    public static void beforeAll() {
        M01SpikeBoot.start();
    }

    @AfterAll
    public static void afterAll() {
        M01SpikeBoot.stop();
    }

    private static IOrmEntity newParent(IOrmSession orm, String code) {
        IOrmEntity parent = orm.newEntity("spike.TestFxParent");
        parent.orm_propValueByName("code", code);
        parent.orm_propValueByName("name", "name-of-" + code);
        return parent;
    }

    private static IEntityModel parentModel() {
        return M01SpikeBoot.orm().getSessionFactory().getOrmModel().getEntityModel("spike.TestFxParent");
    }

    @Test
    public void testStandaloneRecordingSessionCollectsConstructionRows() {
        M01SpikeRecordingSession session = new M01SpikeRecordingSession();
        session.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            IOrmEntity parent = newParent(orm, "P-001");
            orm.save(parent);

            IOrmEntity child = orm.newEntity("spike.TestFxChild");
            child.orm_propValueByName("parentId", parent.orm_propValueByName("id"));
            child.orm_propValueByName("name", "child-one");
            orm.save(child);
            return null;
        }));

        // assert AFTER the session closed: collected data survives unregistration
        Map<String, EntityRow> parents = session.data().get(parentModel());
        assertNotNull(parents, "parent rows must be collected by the standalone hook");
        assertEquals(1, parents.size());
        EntityRow parentRow = parents.values().iterator().next();
        // spike fact: newly-saved rows land in changedData (onSave path); initData is
        // only populated by onLoad — full-row export therefore needs the reload pass
        assertNotNull(parentRow.getChangedData().get("CODE"));
        assertTrue(parentRow.getChangedData().get("ID") != null, "platform-generated id must be captured");
    }

    @Test
    public void testSecondSessionCollectsIndependentlyAfterFirstClosed() {
        M01SpikeRecordingSession first = new M01SpikeRecordingSession();
        first.run(hook -> M01SpikeBoot.orm().runInSession(orm -> orm.save(newParent(orm, "P-A"))));

        M01SpikeRecordingSession second = new M01SpikeRecordingSession();
        second.run(hook -> M01SpikeBoot.orm().runInSession(orm -> {
            orm.save(newParent(orm, "P-B"));
            return null;
        }));

        Map<String, EntityRow> secondParents = second.data().get(parentModel());
        assertNotNull(secondParents);
        assertEquals(1, secondParents.size(), "second session must only collect its own rows");
        EntityRow row = secondParents.values().iterator().next();
        assertEquals("P-B", row.getChangedData().get("CODE"), "first session's P-A row must not leak in");
    }
}
