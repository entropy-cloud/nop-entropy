/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.orm.OrmErrors.ERR_ORM_MIGRATION_WRITE_GATE_CLOSED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 数据搬迁写停止关（设计 §4.3 M3）：nop.orm.migration.write-gate=closed 时 ORM DML
 * 提交入口显式 fail-fast（携带实体与操作），读路径不受影响；gate=open 零行为变更。
 * 覆盖检查点：flush()（级联 DML 提交入口）、flushSave（实体级 save DML）。
 */
public class TestOrmMigrationWriteGate extends AbstractOrmTestCase {

    @Override
    protected void prepareData() {
        // 本测试自建数据
    }

    private SimsCollege newCollege(String id, String name) {
        SimsCollege entity = new SimsCollege();
        entity.setCollegeId(id);
        entity.setCollegeName(name);
        entity.setIntro("intro");
        entity.setShortName("A");
        return entity;
    }

    private List<SimsCollege> findBy(String id) {
        return orm().findAll(SQL.begin()
                .sql("select o from io.nop.app.SimsCollege o where o.collegeId = ?", id).end());
    }

    @Test
    public void testOpenGateAllowsWriteByDefault() {
        assertFalse(OrmMigrationWriteGate.isClosed());
        orm().save(newCollege("g1", "CollegeOpen"));
        orm().flushSession();
        assertEquals(1, findBy("g1").size());
    }

    @Test
    public void testClosedGateFailsFlushOnSavePath() {
        try {
            OrmMigrationWriteGate.setClosed(true);
            // 模板 save 内部经 session.flush() 提交 DML——flush() 检查点先于任何实体落库触发
            NopException e = assertThrows(NopException.class,
                    () -> orm().save(newCollege("g2", "CollegeClosed")));
            assertEquals(ERR_ORM_MIGRATION_WRITE_GATE_CLOSED.getErrorCode(), e.getErrorCode());
            assertEquals("flush", e.getParams().get(OrmErrors.ARG_OPERATION));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
            // 未 flush 的脏会话随基类 tearDown 关闭数据源一并废弃，无需显式清理
        }
    }

    @Test
    public void testClosedGateFailsSaveDirectlyWithEntity() {
        try {
            OrmMigrationWriteGate.setClosed(true);
            SimsCollege entity = newCollege("g3", "CollegeDirect");
            // saveDirectly → flushSave 检查点：携带实体名与操作
            NopException e = assertThrows(NopException.class, () -> orm().saveDirectly(entity));
            assertEquals(ERR_ORM_MIGRATION_WRITE_GATE_CLOSED.getErrorCode(), e.getErrorCode());
            assertEquals("io.nop.app.SimsCollege", e.getParams().get(OrmErrors.ARG_ENTITY_NAME));
            assertEquals("save", e.getParams().get(OrmErrors.ARG_OPERATION));
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testClosedGateDoesNotAffectReads() {
        orm().save(newCollege("g4", "CollegeRead"));
        orm().flushSession();
        try {
            OrmMigrationWriteGate.setClosed(true);
            assertEquals(1, findBy("g4").size(), "read path must stay unaffected by the write gate");
            assertTrue(OrmMigrationWriteGate.isClosed());
        } finally {
            OrmMigrationWriteGate.setClosed(false);
        }
    }

    @Test
    public void testReopenGateRestoresWrites() {
        OrmMigrationWriteGate.setClosed(true);
        assertTrue(OrmMigrationWriteGate.isClosed());
        OrmMigrationWriteGate.setClosed(false);
        orm().save(newCollege("g5", "CollegeReopened"));
        orm().flushSession();
        assertEquals(1, findBy("g5").size());
    }
}
