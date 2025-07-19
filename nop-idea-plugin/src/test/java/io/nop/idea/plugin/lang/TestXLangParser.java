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
                                       x:schema="/path/to/example.xdef"
                              >
                                  <tag name="Child &amp; Tag"/>
                                  <refs>/nop/schema/xdef.xdef,/nop/schema/xdsl.xdef</refs>
                                  <refs>
                                      /nop/schema/xdef.xdef,
                                      /nop/schema/xdsl.xdef
                                  </refs>
                                  <text><![CDATA[
                                      This is CDATA text.
                                  ]]></text>
                                  <mix>
                                      This is a &lt;text/&gt; node.
                                      <tag>
                                          This is a child tag.
                                      </tag>
                                      <tag><![CDATA[
                                          This is a CDATA tag.
                                      ]]></tag>
                                  </mix>
                              </example>
                              """, //
                      "/test/ast/xlang-1.ast" //
        );
    }

    protected void assertASTTree(String code, String expectedAstFile) {
        PsiFile testFile = configureByXLangText(code);

        assertASTTree(testFile, expectedAstFile);
    }
}
