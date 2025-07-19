package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiFile;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptParser extends BaseXLangPluginTestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    public void testParseStatement() {
        assertASTTree("""
                              import java.lang.;
                              const abc = ;
                              const abc = () =>;
                              """, //
                      "/test/ast/xlang-script-err-1.ast" //
        );

        assertASTTree("""
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
                      "/test/ast/xlang-script-1.ast" //
        );
    }

    protected void assertASTTree(String code, String expectedAstFile) {
        PsiFile testFile = myFixture.configureByText("sample." + ext, code);

        assertASTTree(testFile, expectedAstFile);
    }
}
