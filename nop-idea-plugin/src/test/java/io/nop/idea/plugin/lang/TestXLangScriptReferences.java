/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class TestXLangScriptReferences extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testImportReference() {
        // Note: 需确保语法完整

        // 导入包：只能得到光标之前的已存在包
        doTest("import io.nop.xlang.x<caret>def;", "io.nop.xlang");

        // 导入类
        doTest("import io.nop.xlang.x<caret>def.XDefOverride;", "io.nop.xlang.xdef");
        doTest("import io.nop.xlang.xdef.XD<caret>efOverride;", "io.nop.xlang.xdef.XDefOverride");
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text, String expected) {
        myFixture.configureByText("sample." + ext, text);

        PsiReference ref = findReferenceAtCaret();
        assertNotNull(ref);

        PsiElement target = ref.resolve();
        assertNotNull(target);
    }
}
