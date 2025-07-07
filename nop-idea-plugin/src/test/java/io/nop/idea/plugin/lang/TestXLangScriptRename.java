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
                let s = abc + ' abc';
                """, """
                             let data = 123;
                             const def = data + 1;
                             let s = data + ' abc';
                             """);
        assertRename("data", """
                let abc = 123;
                const def = ab<caret>c + 1;
                let s = abc + ' abc';
                """, """
                             let data = 123;
                             const def = data + 1;
                             let s = data + ' abc';
                             """);

        assertRename("data", """
                let ab<caret>c = 'abc';
                abc.trim();
                abc.isEmpty();
                """, """
                             let data = 'abc';
                             data.trim();
                             data.isEmpty();
                             """);
        assertRename("data", """
                let abc = 'abc';
                a<caret>bc.trim();
                abc.isEmpty();
                """, """
                             let data = 'abc';
                             data.trim();
                             data.isEmpty();
                             """);

        assertRename("data", """
                let a<caret>bc = 'abc';
                let def = {abc, def: 123};
                """, """
                             let data = 'abc';
                             let def = {data, def: 123};
                             """);
        assertRename("data", """
                let abc = 'abc';
                let def = {a<caret>bc, def: 123};
                """, """
                             let data = 'abc';
                             let def = {data, def: 123};
                             """);
        assertRename("data", """
                let a<caret>bc = 'abc';
                let def = {abc: abc, def: 123};
                """, """
                             let data = 'abc';
                             let def = {abc: data, def: 123};
                             """);
        assertRename("data", """
                let abc = 'abc';
                let def = {abc: a<caret>bc, def: 123};
                """, """
                             let data = 'abc';
                             let def = {abc: data, def: 123};
                             """);
    }

    public void testRenameFunction() {
        assertRename("fn_1", """
                function f<caret>n(a, b) {}
                const r = fn(1, 2);
                """, """
                             function fn_1(a, b) {}
                             const r = fn_1(1, 2);
                             """);
        assertRename("fn_1", """
                function fn(a, b) {}
                const r = f<caret>n(1, 2);
                """, """
                             function fn_1(a, b) {}
                             const r = fn_1(1, 2);
                             """);

        assertRename("fn_1", """
                const f<caret>n = (a, b) => {};
                const r = fn(1, 2);
                """, """
                             const fn_1 = (a, b) => {};
                             const r = fn_1(1, 2);
                             """);
        assertRename("fn_1", """
                const fn = (a, b) => {};
                const r = f<caret>n(1, 2);
                """, """
                             const fn_1 = (a, b) => {};
                             const r = fn_1(1, 2);
                             """);

        assertRename("aa", """
                function fn(a<caret>1, b1) { return a1 + b1; }
                """, """
                             function fn(aa, b1) { return aa + b1; }
                             """);
        assertRename("aa", """
                function fn(a1, b1) { return a<caret>1 + b1; }
                """, """
                             function fn(aa, b1) { return aa + b1; }
                             """);
        assertRename("bb", """
                function fn(a1, b<caret>1) { return a1 + b1; }
                """, """
                             function fn(a1, bb) { return a1 + bb; }
                             """);
        assertRename("bb", """
                function fn(a1, b1) { return a1 + b<caret>1; }
                """, """
                             function fn(a1, bb) { return a1 + bb; }
                             """);

        assertRename("aa", """
                const fn = (a<caret>1, b1) => a1 + b1;
                """, """
                             const fn = (aa, b1) => aa + b1;
                             """);
        assertRename("aa", """
                const fn = (a1, b1) => a<caret>1 + b1;
                """, """
                             const fn = (aa, b1) => aa + b1;
                             """);
        assertRename("bb", """
                const fn = (a1, b<caret>1) => a1 + b1;
                """, """
                             const fn = (a1, bb) => a1 + bb;
                             """);
        assertRename("bb", """
                const fn = (a1, b1) => a1 + b<caret>1;
                """, """
                             const fn = (a1, bb) => a1 + bb;
                             """);

        assertRename("aa", """
                function fn(a<caret>1, b1) {
                    const c = a1 + 1;
                    return c + a1 + b1;
                }
                """, """
                             function fn(aa, b1) {
                                 const c = aa + 1;
                                 return c + aa + b1;
                             }
                             """);
        assertRename("aa", """
                function fn(a1, b1) {
                    const c = a<caret>1 + 1;
                    return c + a1 + b1;
                }
                """, """
                             function fn(aa, b1) {
                                 const c = aa + 1;
                                 return c + aa + b1;
                             }
                             """);
        assertRename("aa", """
                function fn(a1, b1) {
                    const c = a1 + 1;
                    return c + a<caret>1 + b1;
                }
                """, """
                             function fn(aa, b1) {
                                 const c = aa + 1;
                                 return c + aa + b1;
                             }
                             """);

        assertRename("aa", """
                const fn = (a<caret>1, b1) => {
                    const c = a1 + 1;
                    return c + a1 + b1;
                };
                """, """
                             const fn = (aa, b1) => {
                                 const c = aa + 1;
                                 return c + aa + b1;
                             };
                             """);
        assertRename("aa", """
                const fn = (a1, b1) => {
                    const c = a<caret>1 + 1;
                    return c + a1 + b1;
                };
                """, """
                             const fn = (aa, b1) => {
                                 const c = aa + 1;
                                 return c + aa + b1;
                             };
                             """);
        assertRename("aa", """
                const fn = (a1, b1) => {
                    const c = a1 + 1;
                    return c + a<caret>1 + b1;
                };
                """, """
                             const fn = (aa, b1) => {
                                 const c = aa + 1;
                                 return c + aa + b1;
                             };
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
