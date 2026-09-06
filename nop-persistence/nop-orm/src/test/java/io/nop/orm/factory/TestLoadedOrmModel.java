/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import io.nop.app.SimsCollege;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLoadedOrmModel extends AbstractOrmTestCase {

    /**
     * 复合主键实体的getIdText必须按pkColumns生成逗号分隔的列名列表。
     * 历史版本错误地遍历全部列且逗号位置追加在元素之后，生成非法SQL片段
     */
    @Test
    public void testGetIdTextCompositePk() {
        orm().runInSession(session -> {
            IEntityModel entityModel = session.getLoadedOrmModel().getOrmModel()
                    .requireEntityModel("io.nop.app.SimsExtField");
            // 确认这是复合主键实体
            assertTrue(entityModel.getPkColumns().size() > 1);

            String idText = session.getLoadedOrmModel().getIdText("io.nop.app.SimsExtField", "o");

            String expected = entityModel.getPkColumns().stream()
                    .map(col -> "o." + col.getCode())
                    .collect(Collectors.joining(","));
            assertEquals(expected, idText);

            // 结构性检查：无尾随逗号、列之间都有逗号分隔、列数与主键列数一致
            assertFalse(idText.endsWith(","));
            assertEquals(entityModel.getPkColumns().size(), idText.split(",").length);
            for (IColumnModel col : entityModel.getPkColumns()) {
                assertTrue(idText.contains("o." + col.getCode()));
            }
            return null;
        });
    }

    /**
     * 单列主键实体保持原有行为：o.<code>
     */
    @Test
    public void testGetIdTextSinglePk() {
        orm().runInSession(session -> {
            IEntityModel entityModel = session.getLoadedOrmModel().getOrmModel()
                    .requireEntityModel(SimsCollege.class.getName());
            String idText = session.getLoadedOrmModel().getIdText(SimsCollege.class.getName(), "o");
            assertEquals("o." + entityModel.getPkColumns().get(0).getCode(), idText);
            return null;
        });
    }
}
