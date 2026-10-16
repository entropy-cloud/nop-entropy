/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.utils;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xui.model.UiFormModel;
import io.nop.xui.model.UiGridColModel;
import io.nop.xui.model.UiGridModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * selection 拼接必须尊重发布控制（xmeta published=false）。此前 appendPkFields 无条件把主键
 * 拼进 selection，导致以 not-pub 列为主键的实体（验证码/限流等表以 codeKey 等作主键）生成的
 * 列表页在服务端报 undefined-field；而发布的主键仍需保留。
 */
public class TestXuiViewAnalyzerSelection extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IObjMeta loadMeta(String path) {
        return (IObjMeta) ResourceComponentManager.instance().loadComponentModel(path);
    }

    private static UiGridModel gridWithCol(String id) {
        UiGridModel grid = new UiGridModel();
        UiGridColModel col = new UiGridColModel();
        col.setId(id);
        grid.addCol(col);
        return grid;
    }

    @Test
    public void testNotPublishedPkExcludedFromListSelection() {
        IObjMeta objMeta = loadMeta("/test/notpub-pk.xmeta");

        String selection = XuiHelper.getListSelection(gridWithCol("phone"), objMeta);

        assertTrue(selection.contains("id"), selection);
        assertTrue(selection.contains("phone"), selection);
        assertFalse(selection.contains("codeKey"),
                "not-pub 主键不得进入 selection: " + selection);
    }

    @Test
    public void testNotPublishedPkExcludedFromFormSelection() {
        IObjMeta objMeta = loadMeta("/test/notpub-pk.xmeta");

        String selection = XuiHelper.getFormSelection(new UiFormModel(), objMeta);

        assertFalse(selection.contains("codeKey"),
                "not-pub 主键不得进入 form selection: " + selection);
    }

    @Test
    public void testPublishedPkStillSelected() {
        IObjMeta objMeta = loadMeta("/test/pub-pk.xmeta");

        String selection = XuiHelper.getListSelection(gridWithCol("phone"), objMeta);

        assertTrue(selection.contains("id"), selection);
        assertTrue(selection.contains("phone"), selection);
    }
}
