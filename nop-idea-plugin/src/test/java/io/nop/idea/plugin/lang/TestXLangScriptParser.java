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
        assertParseTree("""
                                import java.lang.;
                                const abc = ;
                                const abc = () =>;
                                """, "/test/ast/statement-err-1.ast");

        assertParseTree("""
                                import java.lang.String;
                                import java.lang.Number;
                                import io.nop.xlang.xdef.domain.XJsonDomainHandler;
                                //
                                const abc = ormTemplate.findListByQuery(query, mapper);
                                //
                                a(1, 2);
                                a.b.c(1, 2);
                                //
                                let abc = new String("abc");
                                let abc = new Abc.Def("abc");
                                let abc = new java.lang.String("abc");
                                const map = new HashMap<String, List>();
                                //
                                let def = new String("def").trim();
                                let def = 123, lmn = 456 + abc;
                                const c = a.b.c;
                                const def = {a, b: 1};
                                const arr = [a, b, c];
                                arr[0] = 'a';
                                //
                                let xyz;
                                xyz = "234";
                                //
                                const b = s instanceof string;
                                //
                                const fn1 = (a, b) => a + b;
                                function fn2(a, b) {
                                    const c = 5;
                                    return a + b + c;
                                }
                                function fn3(a: string, b: number, c: XJsonDomainHandler, d: XJsonDomainHandler.Sub) {
                                    return a + b + c.getName() + d.getName();
                                }
                                //
                                if (a > 2) {
                                    let b = 3;
                                    a.b(b, 1);
                                }
                                """, //
                        "/test/ast/statement-1.ast");
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

    protected String toParseTreeText(@NotNull PsiElement tree) {
        return DebugUtil.psiToString(tree, true, false);
    }
}
