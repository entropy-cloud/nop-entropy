package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiFile;
import io.nop.idea.plugin.BaseXLangPluginTestCase;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-08
 */
public class TestXLangParser extends BaseXLangPluginTestCase {

    public void testParseASTTree() {
        assertASTTree("""
                              <example xmlns:x="/nop/schema/xdsl.xdef"
                                       x:schema="/nop/schema/xdef.xdef"
                              >
                                  <child name="string"/>
                              </example>
                              """, //
                      "/test/ast/xlang-1.ast");
    }

    protected void assertASTTree(String code, String expectedAstFile) {
        PsiFile testFile = configureByXLangText(code);

        assertASTTree(testFile, expectedAstFile);
    }
}
