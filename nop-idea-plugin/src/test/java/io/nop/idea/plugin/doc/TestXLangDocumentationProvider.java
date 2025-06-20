package io.nop.idea.plugin.doc;

import java.util.function.Consumer;

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
        // 显示标签文档
        doTest("""
                       <exam<caret>ple xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child name="Child"/>
                       </example>
                       """, "<p><b>example</b></p><hr/><br/><p>This is root node</p>\n");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <ch<caret>ild name="Child"/>
                       </example>
                       """, "<p><b>child</b></p><hr/><br/><p>This is child node</p>\n");
        // 显示属性文档
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child na<caret>me="Child"/>
                       </example>
                       """, "<p><b>name</b></p><p>stdDomain: <b>string</b></p><hr/><br/><p>This is child name</p>\n");

        // 显示 xdef.xdef 中的 meta:xxx 标签和属性文档
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<meta:unknown-tag ", "<meta:unk<caret>nown-tag "),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:ref=\"XDefNode\"",
                                                                "meta:r<caret>ef=\"XDefNode\""),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));

        // 显示 xdsl.xdef 中的标签和 meta:xxx 属性文档
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("<xdef:unknown-tag ", "<xdef:unk<caret>nown-tag "),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema", "xdsl:sc<caret>hema"),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
    }

    public void testGenerateDocForXmlAttributeValue() {
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child type="lea<caret>f"/>
                       </example>
                       """, "<p><b>leaf-Leaf Node</b></p>");
    }

    private void doTest(String text, String doc) {
        doTest(text, (genDoc) -> assertEquals(doc, genDoc));
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text, Consumer<String> checker) {
        myFixture.configureByText("example." + XLANG_EXT, text);

        // Note: 通过 ApplicationManager.getApplication().runReadAction(() -> {})
        // 消除异常 "Read access is allowed from inside read-action"
        PsiElement originalElement = myFixture.getFile()
                                              .findElementAt(myFixture.getEditor().getCaretModel().getOffset());
        PsiElement element = DocumentationManager.getInstance(getProject())
                                                 .findTargetElement(myFixture.getEditor(), myFixture.getFile());

        DocumentationProvider docProvider = DocumentationManager.getProviderFromElement(originalElement);
        String genDoc = docProvider.generateDoc(element, originalElement);

        checker.accept(genDoc);
    }
}

