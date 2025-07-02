/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class TestXLangScriptReferences extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    /** 测试对导入包/类的引用 */
    public void testImportReference() {
        // Note: 需确保语法完整

        // 导入包：只能得到光标之前的已存在包
        assertReference("import io.nop.xlang.x<caret>def;", "io.nop.xlang.xdef");

        // 导入类
        assertReference("import io.nop.xlang.x<caret>def.XDefOverride;", "io.nop.xlang.xdef");
        assertReference("import io.nop.xlang.xdef.XD<caret>efOverride;", "io.nop.xlang.xdef.XDefOverride");
    }

    /** 测试对对象成员的引用 */
    public void testObjectMemberReference() {
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const handler = new XJsonDomainHandler();
                                handler.get<caret>Name();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler#getName()");
    }

    public void testJavaClassMemberReference() {
        assertJavaReference("""
                                    public class Sample {
                                        public static void main(String[] args) {
                                            String a = " abc ".tr<caret>im();
                                        }
                                    }
                                    """, "");
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void assertReference(String text, String expected) {
        assertReference("sample." + ext, text, expected);
    }

    private void assertJavaReference(String text, String expected) {
        assertReference("Sample.java", text, expected);
    }

    private void assertReference(String fileName, String text, String expected) {
        myFixture.configureByText(fileName, text);

        PsiReference ref = findReferenceAtCaret();
        assertNotNull(ref);

        PsiElement target = ref.resolve();
        assertNotNull(target);

        if (target instanceof PsiClass cls) {
            String actual = cls.getQualifiedName();
            assertEquals(expected, actual);
        } else if (target instanceof PsiPackage pkg) {
            String actual = pkg.getQualifiedName();
            assertEquals(expected, actual);
        } else {
            fail("Unknown target " + target);
        }
    }
}
