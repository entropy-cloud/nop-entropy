/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.enc.EncAssistedHelper;
import io.nop.app.SimsExam;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.orm.OrmErrors.ERR_ORM_ENC_HELPER_COLUMN_READONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2318 Phase 2：enc 主列的辅助列（assisted/like）在保存/更新路径上的自动派生与只读守护。
 * 声明样例见 test orm 模型 SimsExam（examName -> examNameAssisted / examNameLike）。
 * 生成实体类尚未包含辅助列属性，测试按 propId 访问（对代码生成不敏感）。
 */
public class TestEncAssistedDerivedColumns extends AbstractOrmTestCase {

    private int propId(String propName) {
        return sessionFactory.getOrmModel().requireEntityModel("io.nop.app.SimsExam")
                .requireColumn(propName).getPropId();
    }

    @Test
    public void testDerivedOnSave() {
        SimsExam entity = new SimsExam();
        entity.setExamId("201");
        entity.setExamName("testExam");
        daoProvider().daoFor(SimsExam.class).saveEntity(entity);

        String expectedAssisted = EncAssistedHelper.instance().assistedDigest("testExam");
        String expectedLike = EncAssistedHelper.instance().likeDigest("testExam");

        Map<String, Object> row = jdbc().findFirst(new SQL("select * from sims_exam where exam_id='201'"));
        assertEquals(expectedAssisted, row.get("EXAM_NAME_ASSISTED"));
        assertEquals(expectedLike, row.get("EXAM_NAME_LIKE"));

        // 摘要契约形状：assisted 定长 64 hex；like 长度 = width * len(plain)
        assertEquals(64, expectedAssisted.length());
        assertTrue(expectedAssisted.matches("[0-9a-f]+"));
        assertEquals(8 * "testExam".length(), expectedLike.length());
    }

    @Test
    public void testDerivedOnUpdateAndNull() {
        orm().runInSession(() -> {
            SimsExam entity = new SimsExam();
            entity.setExamId("202");
            entity.setExamName("before");
            daoProvider().daoFor(SimsExam.class).saveEntity(entity);
        });

        orm().runInSession(() -> {
            SimsExam loaded = daoProvider().daoFor(SimsExam.class).getEntityById("202");
            loaded.setExamName("after");
            daoProvider().daoFor(SimsExam.class).updateEntity(loaded);
        });

        Map<String, Object> row = jdbc().findFirst(new SQL("select * from sims_exam where exam_id='202'"));
        assertEquals(EncAssistedHelper.instance().assistedDigest("after"), row.get("EXAM_NAME_ASSISTED"));
        assertEquals(EncAssistedHelper.instance().likeDigest("after"), row.get("EXAM_NAME_LIKE"));

        orm().runInSession(() -> {
            SimsExam loaded2 = daoProvider().daoFor(SimsExam.class).getEntityById("202");
            loaded2.setExamName(null);
            daoProvider().daoFor(SimsExam.class).updateEntity(loaded2);
        });

        Map<String, Object> row2 = jdbc().findFirst(new SQL("select * from sims_exam where exam_id='202'"));
        assertNull(row2.get("EXAM_NAME_ASSISTED"));
        assertNull(row2.get("EXAM_NAME_LIKE"));
    }

    @Test
    public void testUserAssignmentRejectedOnSave() {
        int helperPropId = propId("examNameAssisted");
        // 生成实体类尚未包含辅助列属性，无法直接赋值；
        // 用匿名子类在守护的观察点（orm_propInited）模拟"用户已对辅助列赋值"的新实体状态
        SimsExam entity = new SimsExam() {
            @Override
            public boolean orm_propInited(int propId) {
                return propId == helperPropId || super.orm_propInited(propId);
            }
        };
        entity.setExamId("203");
        entity.setExamName("x");

        NopException e = assertThrows(NopException.class,
                () -> daoProvider().daoFor(SimsExam.class).saveEntity(entity));
        assertEquals(ERR_ORM_ENC_HELPER_COLUMN_READONLY.getErrorCode(), e.getErrorCode());
        assertEquals("examNameAssisted", e.getParam("propName"));
    }

    @Test
    public void testUserAssignmentRejectedOnUpdate() {
        SimsExam entity = new SimsExam();
        entity.setExamId("204");
        entity.setExamName("origin");
        daoProvider().daoFor(SimsExam.class).saveEntity(entity);

        NopException e = assertThrows(NopException.class, () -> orm().runInSession(() -> {
            SimsExam loaded = daoProvider().daoFor(SimsExam.class).getEntityById("204");
            // load 后辅助列已派生；用户改动主列的同时篡改辅助列必须显式失败
            loaded.setExamName("changed");
            loaded.orm_propValue(propId("examNameAssisted"), "forged");
            daoProvider().daoFor(SimsExam.class).updateEntity(loaded);
        }));
        assertEquals(ERR_ORM_ENC_HELPER_COLUMN_READONLY.getErrorCode(), e.getErrorCode());
    }
}
