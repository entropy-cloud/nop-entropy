package io.nop.task.ext.dao;

import io.nop.core.initialize.CoreInitialization;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 3 [维度03-01]：ORM 模型索引守卫。
 *
 * <p>{@code nop_task_step_instance} 此前无任何二级索引，步骤状态读写
 * （{@code DaoTaskStateStore.findStepEntity} 的 eq(taskInstanceId) AND eq(stepPath)）全表扫描。
 * 源模型 {@code nop-task/model/nop-task.orm.xml} 现声明单列索引 IX_TASK_STEP_TASK_ID
 * （stepPath VARCHAR(2000) 参与 MySQL utf8mb4 复合索引超 3072 字节键长上限，故单列——plan-audit 裁定）。
 *
 * <p>注意：CREATE TABLE DDL 不产出二级索引（platform DdlSqlCreator 限制），存量库需执行
 * nop-task/deploy/sql 各方言目录下的 upgrade-nop-task-step-instance-index.sql。本测试断言的是
 * 合并后 ORM 模型（_app.orm.xml）中索引声明的存在与列正确性。
 */
public class TestTaskOrmModelIndex {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void stepInstanceEntityDeclaresTaskInstanceIdLookupIndex() {
        OrmModel model = (OrmModel) new DslModelParser()
                .parseFromResource(io.nop.core.resource.VirtualFileSystem.instance()
                        .getResource("/nop/task/orm/_app.orm.xml"));
        model.init();
        OrmEntityModel entity = model.getEntity("io.nop.task.dao.entity.NopTaskStepInstance");
        assertNotNull(entity, "NopTaskStepInstance entity must exist in merged orm model");

        OrmIndexModel index = entity.getIndexes() == null ? null
                : entity.getIndexes().stream()
                        .filter(ix -> "IX_TASK_STEP_TASK_ID".equals(ix.getName()))
                        .findFirst().orElse(null);
        assertNotNull(index, "IX_TASK_STEP_TASK_ID must be declared on NopTaskStepInstance (03-01)");
        assertNotNull(index.getColumns());
        assertTrue(index.getColumns().size() == 1
                        && "taskInstanceId".equals(index.getColumns().get(0).getName()),
                "index must be single-column on taskInstanceId, got: "
                        + index.getColumns().stream().map(c -> c.getName()).toList());
    }
}
