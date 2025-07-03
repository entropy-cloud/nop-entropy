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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
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
                                "abc".tr<caret>im();
                                """, "java.lang.String#trim");

        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const handler = new XJsonDomainHandler();
                                handler.get<caret>Name();
                                // 尝试触发无限递归
                                let name = handler.getName();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler#getName");

        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const handler = new XJsonDomainHandler();
                                handler.instance().get<caret>Name();
                                // 尝试触发无限递归
                                let name = handler.getName();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler#getName");

        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const handler = XJsonDomainHandler.INST<caret>ANCE;
                                // 尝试触发无限递归
                                let name = handler.getName();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler#INSTANCE");

        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                let name = XJsonDomainHandler.INSTANCE.get<caret>Name();
                                // 尝试触发无限递归
                                const handler = new XJsonDomainHandler();
                                name = handler.getName();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler#getName");
    }

    public void testVarReference() {
        assertReference("""
                                let abc = "abc";
                                const def = a<caret>bc + "def";
                                """, "");
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
        } //
        else if (target instanceof PsiMethod method) {
            String actual = method.getContainingClass().getQualifiedName() + "#" + method.getName();
            assertEquals(expected, actual);
        } //
        else if (target instanceof PsiField field) {
            String actual = field.getContainingClass().getQualifiedName() + "#" + field.getName();
            assertEquals(expected, actual);
        } //
        else if (target instanceof PsiPackage pkg) {
            String actual = pkg.getQualifiedName();
            assertEquals(expected, actual);
        } //
        else {
            fail("Unknown target " + target);
        }
    }
}
