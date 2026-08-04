package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.OrmEntityModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P2-MA2-03 修复回归测试（plan-2026-08-04-1004-3，MA2.1 裁决例外）：
 * SQL 保留字列 code 改名——Oracle DDL 未引号直出 `PRIMARY SMALLINT default 0` /
 * `CONSTRAINT VARCHAR2(100)` 为语法错误（MySQL 反引号可解析），裁决依据以 Oracle 事实为准。
 *
 * <p>断言：NopMetaEntityField.primaryField → 列 code `IS_PRIMARY`；
 * NopMetaEntityUniqueKey.constraintName → 列 code `CONSTRAINT_NAME`（Java 属性名不变，无契约影响；
 * 生成 DDL 不再包含裸保留字列名）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaReservedWordColumns extends JunitBaseTestCase {

    public TestNopMetaReservedWordColumns() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IOrmTemplate orm;

    @Test
    public void testPrimaryFieldColumnCodeRenamedFromReservedWord() {
        assertColumnCode("io.nop.metadata.dao.entity.NopMetaEntityField", "primaryField", "IS_PRIMARY");
    }

    @Test
    public void testConstraintNameColumnCodeRenamedFromReservedWord() {
        assertColumnCode("io.nop.metadata.dao.entity.NopMetaEntityUniqueKey", "constraintName", "CONSTRAINT_NAME");
    }

    private void assertColumnCode(String entityName, String propName, String expectedCode) {
        OrmEntityModel model = (OrmEntityModel) orm.getOrmModel().getEntityModel(entityName);
        assertNotNull(model, entityName + " model must be loaded");
        IColumnModel col = model.getColumn(propName);
        assertNotNull(col, entityName + " must declare column " + propName);
        assertEquals(expectedCode, col.getCode(),
                propName + " must map to column code " + expectedCode
                        + " (SQL reserved words must not be used as bare column codes)");
    }
}
