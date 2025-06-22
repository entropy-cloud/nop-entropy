package io.nop.idea.plugin.doc;

import java.util.function.Consumer;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.xlang.xdef.XDefConstants;

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
        // - 确定属性
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child na<caret>me="Child"/>
                       </example>
                       """, "<p><b>name</b></p><p>stdDomain: <b>string</b></p><hr/><br/><p>This is child name</p>\n");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child ty<caret>pe="leaf"/>
                       </example>
                       """, "<p><b>type</b></p><p>stdDomain: <b>dict:test/doc/child-type</b></p>");
        // - 未确定属性
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child ag<caret>e="22"/>
                       </example>
                       """, "<p><b>age</b></p><p>stdDomain: <b>any</b></p><hr/><br/><p>This a unknown attribute</p>\n");

        // 显示 xdef.xdef 中的 meta:xxx 标签和属性文档
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<meta:unknown-tag ", "<meta:unk<caret>nown-tag "),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:ref=\"XDefNode\"",
                                                                "meta:r<caret>ef=\"XDefNode\""),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("xmlns:meta", "xmlns:m<caret>eta"), (genDoc) -> {
            assertFalse(genDoc.contains("<hr/>"));
            assertTrue(genDoc.contains("xmlns:meta"));
            assertTrue(genDoc.contains(XDefConstants.STD_DOMAIN_XDEF_REF));
        });
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<meta:unknown-tag ", "<meta:unkn<caret>own-tag "),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:unknown-tag meta:ref=",
                                                                "<xdef:unknown<caret>-tag meta:ref="),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:unknown-tag meta:ref=",
                                                                "<xdef:unknown-tag meta:r<caret>ef="),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unknown-attr=\"string\"",
                                                                "meta:unknown-<caret>attr=\"string\""),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:prop name=\"!xml-name\"",
                                                                "<xdef:prop na<caret>me=\"!xml-name\""),
               (genDoc) -> assertFalse(genDoc.contains("<hr/>")));

        // 显示 xdsl.xdef 中的标签和 meta:xxx 属性文档
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("<xdef:unknown-tag ", "<xdef:unk<caret>nown-tag "),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema", "xdsl:sc<caret>hema"),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("<x:gen-extends xdef:value=",
                                                                "<x:gen-extends xdef:<caret>value="),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("<x:post-parse ", "<x:post<caret>-parse "),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));

        // 显示 xpl 类型子节点文档
        doTest(readVfsResource("/test/doc/example.xdef").replace("xpl:dump=\"true\"", "xpl:du<caret>mp=\"true\""),
               (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xmeta.xdef"
                       >
                           <x:gen-extends>
                               <meta-gen:DefaultMetaGenExtends xpl:li<caret>b="/nop/core/xlib/meta-gen.xlib"/>
                           </x:gen-extends>
                       </meta>
                       """, (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xdef.xdef"
                       >
                           <xdef:pre-parse>
                               <meta-gen:DefaultMetaGenExtends xpl:li<caret>b="/nop/core/xlib/meta-gen.xlib"/>
                           </xdef:pre-parse>
                       </meta>
                       """, (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
        doTest("""
                       <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                          <tags>
                              <GenXxx outputMode="node">
                                  <source>
                                      <meta-gen:DefaultMetaGenExtends xpl:li<caret>b="/nop/core/xlib/meta-gen.xlib"/>
                                  </source>
                              </GenPage>
                          </tags>
                       </lib>
                       """, (genDoc) -> assertTrue(genDoc.contains("<hr/>")));
    }

    public void testGenerateDocForXmlAttributeValue() {
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                           <child type="lea<caret>f"/>
                       </example>
                       """, "<p><b>leaf - Leaf Node</b></p>");
    }

    private void doTest(String text, String doc) {
        doTest(text, (genDoc) -> assertEquals(doc, genDoc));
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text, Consumer<String> checker) {
        myFixture.configureByText("example." + XLANG_EXT, text);

        String genDoc = getDoc();

        checker.accept(genDoc);
    }
}

