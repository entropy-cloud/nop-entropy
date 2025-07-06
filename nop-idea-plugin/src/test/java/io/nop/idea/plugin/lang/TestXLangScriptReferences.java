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
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;

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
                                let a = b;
                                "abc".tr<caret>im();
                                let a = b;
                                """, "java.lang.String#trim");
        assertReference("""
                                let abc = 123;
                                abc.byte<caret>Value();
                                let a = b;
                                """, "java.lang.Integer#byteValue");
        assertReference("""
                                let abc = 123;
                                abc = "abc";
                                abc.tr<caret>im();
                                let a = b;
                                """, "java.lang.String#trim");

        assertReference("""
                                Integer.val<caret>ueOf;
                                """, "java.lang.Integer#valueOf");
        assertReference("""
                                Integer.val<caret>ueOf();
                                """, "java.lang.Integer#valueOf");
        assertReference("""
                                Integer.val<caret>ueOf('123');
                                """, "java.lang.Integer#valueOf");

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
                                abc = 123;
                                """, "@4");
        assertReference("""
                                let abc = "abc";
                                ab<caret>c.trim();
                                """, "@4");

        assertReference("""
                                const def = [1, 2, 3];
                                d<caret>ef[0] = 2;
                                """, "@6");

        assertReference("""
                                const s = new St<caret>ring("abc");
                                """, "java.lang.String");
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const handler = new XJsonDo<caret>mainHandler();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler");
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const sub = new XJsonDomainHandler.S<caret>ub.Sub();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub");
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const sub = new XJsonDomainHandler.Sub.S<caret>ub();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.Sub");
        assertReference("""
                                const handler = new io.nop.xlang.xd<caret>ef.domain.XJsonDomainHandler();
                                """, "io.nop.xlang.xdef");
        assertReference("""
                                const handler = new io.nop.xlang.xdef.domain.XJsonDo<caret>mainHandler();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler");
        assertReference("""
                                const sub = new io.nop.xlang.xdef.domain.XJsonD<caret>omainHandler.Sub.Sub();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler");
        assertReference("""
                                const sub = new io.nop.xlang.xdef.domain.XJsonDomainHandler.S<caret>ub.Sub();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub");
        assertReference("""
                                const sub = new io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.S<caret>ub();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.Sub");

        assertReference("""
                                const handler = new io.nop.xlang.xdef.domain.XJsonDomainHandler();
                                handler.get<caret>Name();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler#getName");
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                const sub = new XJsonDomainHandler.Sub.Sub();
                                sub.get<caret>Name();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.Sub#getName");
        assertReference("""
                                const sub = new io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.Sub();
                                sub.get<caret>Name();
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.Sub#getName");

        assertReference("""
                                const a1 = 'a';
                                const b1 = 1;
                                const obj = {a<caret>1, b1: b1};
                                """, "@6");
        assertReference("""
                                const a1 = 'a';
                                const b1 = 1;
                                const obj = {a1, b1: b<caret>1};
                                """, "@22");
        assertReference("""
                                const a1 = 'a';
                                const b1 = 1;
                                const c1 = 'c';
                                const obj = {a1, c<caret>1};
                                """, "@36");
    }

    public void testFunctionReference() {
        assertReference("""
                                function fn1(a, b) { return a + b; }
                                fn<caret>1(1, 2);
                                """, "@9");
        assertReference("""
                                function fn1(a, b) { return a + b; }
                                const a = fn<caret>1(1, 2);
                                """, "@9");
        assertReference("""
                                const fn1 = (a1, b1) => a1 + b1;
                                fn<caret>1(1, 2);
                                """, "@6");
        assertReference("""
                                const fn1 = (a1, b1) => a1 + b1;
                                const a = fn<caret>1(1, 2);
                                """, "@6");

        // 对函数参数的引用
        assertReference("""
                                function fn1(a1, b1) {
                                    return a<caret>1 + b1;
                                }
                                """, "@13");
        assertReference("""
                                function fn1(a1, b1) {
                                    return a1 + b<caret>1;
                                }
                                """, "@17");
        assertReference("""
                                const fn1 = (a1, b1) => a<caret>1 + b1;
                                """, "@13");
        assertReference("""
                                const fn1 = (a1, b1) => a1 + b<caret>1;
                                """, "@17");

        // 对函数参数类型的引用
        assertReference("""
                                function fn1(a1: st<caret>ring, b1: number) {
                                    return a1 + b1;
                                }
                                """, "java.lang.String");
        assertReference("""
                                function fn1(a1: string, b1: num<caret>ber) {
                                    return a1 + b1;
                                }
                                """, "java.lang.Number");

        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                function fn1(a1: string, b1: XJsonDom<caret>ainHandler) {
                                    return a1 + b1;
                                }
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler");
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                function fn1(a1: string, b1: XJsonDomainHandler.S<caret>ub.Sub) {
                                    return a1 + b1;
                                }
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub");
        assertReference("""
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                function fn1(a1: XJsonDomainHandler.Sub.Sub) {
                                    return a1.get<caret>Name();
                                }
                                """, "io.nop.xlang.xdef.domain.XJsonDomainHandler.Sub.Sub#getName");

        assertReference("""
                                const fn1 = (a1: st<caret>ring, b1: number) => a1 + b1;
                                """, "java.lang.String");
        assertReference("""
                                const fn1 = (a1: string, b1: num<caret>ber) => a1 + b1;
                                """, "java.lang.Number");

        assertReference("""
                                const b = s instanceof str<caret>ing;
                                """, "java.lang.String");
        assertReference("""
                                const b = s instanceof num<caret>ber;
                                """, "java.lang.Number");

//        // TODO 对函数调用的结果类型的引用
//        assertReference("""
//                                function fn1(a, b) { return a + b; }
//                                const a = fn1('a', 'b');
//                                a.tri<caret>m();
//                                """, "java.lang.String#trim");
//        assertReference("""
//                                function fn1(a, b) { return a + b; }
//                                const a = fn1('a', 'b');
//                                a.trim();
//                                const b = fn1(1, 2);
//                                b.byte<caret>Value();
//                                """, "java.lang.Integer#byteValue");
    }

    public void testGenericTypeReference() {
        assertReference("""
                                import java.util.HashMap;
                                import java.util.List;
                                const s = new Has<caret>hMap<String, List>();
                                """, "java.util.HashMap");
        assertReference("""
                                import java.util.HashMap;
                                import java.util.List;
                                const s = new HashMap<St<caret>ring, List>();
                                """, "java.lang.String");
        assertReference("""
                                import java.util.HashMap;
                                import java.util.List;
                                const s = new HashMap<String, Li<caret>st>();
                                """, "java.util.List");
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void assertReference(String text, String expected) {
        myFixture.configureByText("sample." + ext, text);

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
        else if (target instanceof IdentifierNode id) {
            assertEquals(expected, "@" + id.getTextOffset());
        } //
        else {
            fail("Unknown target " + target);
        }
    }
}
