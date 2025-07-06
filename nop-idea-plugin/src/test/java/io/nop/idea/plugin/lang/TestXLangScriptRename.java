/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiFile;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class TestXLangScriptRename extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testRenameVar() {
        assertRename("data", """
                let a<caret>bc = 123;
                const def = abc + 1;
                """, """
                             let data = 123;
                             const def = data + 1;
                             """);
    }

    public void testRenameJava() {
        assertJavaRename("name", """
                package io.nop.xlang.xdef;
                public class Sample {
                    public void get<caret>Name() {}
                }
                """, """
                                 import io.nop.xlang.xdef.Sample;
                                 let s = new Sample();
                                 s.getName();
                                 """, """
                                 import io.nop.xlang.xdef.Sample;
                                 let s = new Sample();
                                 s.name();
                                 """);
        assertJavaRename("name", """
                package io.nop.xlang.xdef;
                public class Sample {
                    public void get<caret>Name() {}
                }
                """, """
                                 let s = new io.nop.xlang.xdef.Sample();
                                 s.getName();
                                 """, """
                                 let s = new io.nop.xlang.xdef.Sample();
                                 s.name();
                                 """);

        assertJavaRename("Sample123", """
                package io.nop.xlang.xdef;
                public class Sa<caret>mple { }
                """, """
                                 import io.nop.xlang.xdef.Sample;
                                 let s = new Sample();
                                 """, """
                                 import io.nop.xlang.xdef.Sample123;
                                 let s = new Sample123();
                                 """);
        assertJavaRename("Sample456", """
                package io.nop.xlang.xdef;
                public class Sa<caret>mple { }
                """, """
                                 let s = new io.nop.xlang.xdef.Sample();
                                 """, """
                                 let s = new io.nop.xlang.xdef.Sample456();
                                 """);
        assertJavaRename("Sample789", """
                package io.nop.xlang.xdef;
                public class Sa<caret>mple { }
                """, """
                                 let s = new io.nop.xlang.xdef. Sample();
                                 """, """
                                 let s = new io.nop.xlang.xdef. Sample789();
                                 """);

        assertJavaRename("username", """
                package io.nop.xlang.xdef;
                public class Sample { public final String na<caret>me; }
                """, """
                                 import io.nop.xlang.xdef.Sample;
                                 let s = new Sample();
                                 let name = s.name;
                                 """, """
                                 import io.nop.xlang.xdef.Sample;
                                 let s = new Sample();
                                 let name = s.username;
                                 """);
        assertJavaRename("username", """
                package io.nop.xlang.xdef;
                public class Sample { public final String na<caret>me; }
                """, """
                                 let s = new io.nop.xlang.xdef.Sample();
                                 let name = s.name;
                                 """, """
                                 let s = new io.nop.xlang.xdef.Sample();
                                 let name = s.username;
                                 """);

        assertJavaRename("xpl", """
                package io.nop.xl<caret>ang.xdef;
                public class Sample { }
                """, """
                                 import io.nop.xlang.xdef.Sample;
                                 let s = new io.nop.xlang.xdef.Sample();
                                 """, """
                                 import io.nop.xpl.xdef.Sample;
                                 let s = new io.nop.xpl.xdef.Sample();
                                 """);
    }

    protected void assertRename(String newName, String text, String expectedText) {
        myFixture.configureByText("sample." + ext, text);
        myFixture.renameElementAtCaret(newName);

        myFixture.checkResult(expectedText);
    }

    protected void assertJavaRename(String newName, String javaText, String sampleText, String expectedText) {
        PsiFile testFile = myFixture.configureByText("sample." + ext, sampleText);

        myFixture.configureByText("Sample.java", javaText);
        myFixture.renameElementAtCaret(newName);

        assertEquals(expectedText, testFile.getText());
    }
}
