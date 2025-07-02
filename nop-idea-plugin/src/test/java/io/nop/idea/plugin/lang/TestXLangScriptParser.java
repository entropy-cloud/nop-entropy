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
//        assertParseTree("import java.lang.;", "/test/ast/statement-err-1.ast");
//        assertParseTree("const abc = ", "/test/ast/statement-err-2.ast");
//        assertParseTree("const abc = () =>", "/test/ast/statement-err-3.ast");
//
//        assertParseTree("import java.lang.String;", "/test/ast/statement-1.ast");
//
//        assertParseTree("""
//                                import java.lang.String;
//                                import java.lang.Number;
//                                """, "/test/ast/statement-2.ast");
//
//        assertParseTree("""
//                                import java.lang.String;
//                                import java.lang.Number;
//                                const abc = new String("abc");
//                                const def = 123;
//                                """, "/test/ast/statement-3.ast");
//
//        assertParseTree("""
//                                import java.lang.Number;
//                                const fn1 = (a, b) => a + b;
//                                function fn2(a, b) {
//                                    return a + b;
//                                }
//                                function fn3(a: string, b: number) {
//                                    return a + b;
//                                }
//                                """, "/test/ast/statement-4.ast");

        assertParseTree("""
                                const abc = ormTemplate.findListByQuery(query, mapper);
                                query.addFilter(filter(query, svcCtx));
                                a(1, 2);
                                a.b.c(1, 2);
                                const c = a.b.c;
                                const def = {a, b: 1};
                                """, //
                        "/test/ast/statement-5.ast");
    }

    public void testJavaParseTree() {
        assertJavaParseTree("""
                                    public class Sample {
                                        public static void main(String[] args) {
                                            String s1 = StringHelper.trim(b);
                                            String s2 = a.b.c(1, 2);
                                            String s3 = a.b.c.e;
                                            some.other.another.start();
                                        }
                                    }
                                    """);
    }

    protected void assertParseTree(String code, String expectedAstFile) {
        PsiFile testFile = myFixture.configureByText("sample." + ext, code);
        String testTree = toParseTreeText(testFile);

        String expectedTree = readVfsResource(expectedAstFile);

        assertEquals(expectedTree, testTree);
    }

    protected void assertJavaParseTree(String code) {
        PsiFile testFile = myFixture.configureByText("Sample.java", code);
        String testTree = toParseTreeText(testFile);

        assertEquals("", testTree);
    }

    protected String toParseTreeText(@NotNull PsiElement file) {
        return DebugUtil.psiToString(file, false, false);
    }
}
