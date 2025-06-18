package io.nop.idea.plugin.doc;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.BaseXLangPluginTestCase;

/**
 * 参考 https://github.com/JetBrains/intellij-community/blob/master/xml/tests/src/com/intellij/html/HtmlDocumentationTest.java
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-17
 */
public class TestXLangDocumentationProvider extends BaseXLangPluginTestCase {
    private static final String XLANG_EXT = "xdoc";

    @Override
    protected String[] getXLangFileExtensions() {
        return new String[] { XLANG_EXT };
    }

    public void testGenerateDocForXmlName() {
        doTest("""
                       <exam<caret>ple xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child name="Child"/>
                       </example>
                       """, "<p>This is root node</p>\n");

        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <ch<caret>ild name="Child"/>
                       </example>
                       """, "<p>This is child node</p>\n");

        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child na<caret>me="Child"/>
                       </example>
                       """, "<p>stdDomain=string</p><hr/><br/><p>This is child name</p>\n");
    }

    public void testGenerateDocForXmlAttributeValue() {
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child type="lea<caret>f"/>
                       </example>
                       """, "<p><b>leaf-Leaf Node</b></p>");
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text, String doc) {
        myFixture.configureByText("example." + XLANG_EXT, text);

        // Note: 通过 ApplicationManager.getApplication().runReadAction(() -> {})
        // 消除异常 "Read access is allowed from inside read-action"
        PsiElement originalElement = myFixture.getFile()
                                              .findElementAt(myFixture.getEditor().getCaretModel().getOffset());
        PsiElement element = DocumentationManager.getInstance(getProject())
                                                 .findTargetElement(myFixture.getEditor(), myFixture.getFile());

        DocumentationProvider docProvider = DocumentationManager.getProviderFromElement(originalElement);
        String genDoc = docProvider.generateDoc(element, originalElement);

        assertEquals(doc, genDoc);
    }
}

