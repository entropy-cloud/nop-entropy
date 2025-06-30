package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptParser extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testParseStatement() {
//        checkMatchJavaParseTree("import java.lang.;", "/test/ast/statement-err-1.ast");
//        checkMatchJavaParseTree("const abc = ", "/test/ast/statement-err-2.ast");
//        checkMatchJavaParseTree("const abc = () =>", "/test/ast/statement-err-3.ast");
//
//        checkMatchJavaParseTree("import java.lang.String;", "/test/ast/statement-1.ast");
//
//        checkMatchJavaParseTree("""
//                                        import java.lang.String;
//                                        import java.lang.Number;
//                                        """, "/test/ast/statement-2.ast");
//
//        checkMatchJavaParseTree("""
//                                        import java.lang.String;
//                                        import java.lang.Number;
//                                        const abc = new String("abc");
//                                        const def = 123;
//                                        """, "/test/ast/statement-3.ast");
//
//        checkMatchJavaParseTree("""
//                                        import java.lang.Number;
//                                        const fn1 = (a, b) => a + b;
//                                        function fn2(a, b) {
//                                            return a + b;
//                                        }
//                                        function fn3(a: string, b: number) {
//                                            return a + b;
//                                        }
//                                        """, "/test/ast/statement-4.ast");

        checkMatchJavaParseTree("""
                                        const abc = ormTemplate.findListByQuery(query, mapper);
                                        query.addFilter(filter(query, svcCtx));
                                        a.b.c(1, 2, 3);
                                        const def = {a, b: 1};
                                        """, //
                                "/test/ast/statement-5.ast");
    }

    protected void checkMatchJavaParseTree(String code, String expectedAstFile) {
        PsiFile testFile = myFixture.configureByText("sample." + ext, code);
        String testTree = toParseTreeText(testFile);

        String expectedTree = readVfsResource(expectedAstFile);

        assertEquals(expectedTree, testTree);
    }

    protected String toParseTreeText(@NotNull PsiElement file) {
        return DebugUtil.psiToString(file, false, true);
    }
}
