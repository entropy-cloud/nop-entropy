/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import java.util.List;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptCompletions extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testImportCompletion() {
        // Note: 不能构造仅匹配唯一结果的样本，以避免 myFixture.getLookupElementStrings() 返回 null 或空结果
        String[] samples = new String[] {
                // 对包的补全
                "io.nop.xlang.", //
                "io.nop.x", //
                // 对类的补全
                "io.nop.xlang.xdef.XD", //
        };

        for (String sample : samples) {
            myFixture.configureByText("sample." + ext, "import " + sample + "<caret>;");
            myFixture.completeBasic();

            // Note: 在仅有唯一匹配时，得到的结果为 null 或空
            List<String> items = myFixture.getLookupElementStrings();
            assertNotNull(items);
            assertFalse(items.isEmpty());

            String expected = "import " + sample.replaceAll("\\.[^.]+$", ".") + items.get(0) + ";";
            assertCompletion(expected);
        }
    }
}
