/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.doc;

import java.util.function.Consumer;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import junit.framework.TestCase;

/**
 * 参考 https://github.com/JetBrains/intellij-community/blob/master/xml/tests/src/com/intellij/html/HtmlDocumentationTest.java
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-17
 */
public class TestXLangDocumentationProvider extends BaseXLangPluginTestCase {

    public void testGenerateDocForTag() {
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<meta:unknown-tag x:schema", //
                                   "<meta:unkn<caret>own-tag x:schema"), //
                  (doc) -> {
                      assertTrue(doc.contains("自举定义"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<meta:unknown-tag meta:ref", //
                                   "<me<caret>ta:unknown-tag meta:ref"), //
                  (doc) -> {
                      assertTrue(doc.contains("不会匹配xdef:unknown-tag"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<meta:define meta:name", //
                                   "<meta:def<caret>ine meta:name"), //
                  TestCase::assertNull //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:unknown-tag meta:ref", //
                                   "<xd<caret>ef:unknown-tag meta:ref"), //
                  (doc) -> {
                      assertTrue(doc.contains("所有属性和节点都必须明确声明"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:define xdef:name", //
                                   "<xdef:de<caret>fine xdef:name"), //
                  (doc) -> {
                      assertTrue(doc.contains("定义xdef片段"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:prop name", //
                                   "<xd<caret>ef:prop name"), //
                  (doc) -> {
                      assertTrue(doc.contains("扩展属性"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );

        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "<x:post-parse xdef:value", //
                                   "<x:post-<caret>parse xdef:value"), //
                  (doc) -> {
                      assertTrue(doc.contains("之后执行此回调函数"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "<xdef:unknown-tag xdsl:schema", //
                                   "<x<caret>def:unknown-tag xdsl:schema"), //
                  (doc) -> {
                      assertTrue(doc.contains("只在合并过程中存在"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "<xdef:unknown-tag xdef:value", //
                                   "<xdef:unk<caret>nown-tag xdef:value"), //
                  (doc) -> {
                      assertFalse(doc.contains("<hr/>"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );

        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "</xdef:post-parse>", //
                                   "</xdef:po<caret>st-parse>"), //
                  (doc) -> {
                      assertTrue(doc.contains("xdef:post-parse"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "<xdef:unknown-tag", //
                                   "<xd<caret>ef:unknown-tag"), //
                  (doc) -> {
                      assertTrue(doc.contains("Any child node"));
                      assertFalse(doc.contains("/test/doc/example.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "<child name", //
                                   "<ch<caret>ild name"), //
                  (doc) -> {
                      assertTrue(doc.contains("This is child node"));
                      assertFalse(doc.contains("/test/doc/example.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "</example>", //
                                   "</exa<caret>mple>"), //
                  (doc) -> {
                      assertTrue(doc.contains("This is root node"));
                      assertFalse(doc.contains("/test/doc/example.xdef"));
                  } //
        );

        assertDoc("""
                          <exam<caret>ple xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <child name="Child"/>
                          </example>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("This is root node"));
                      assertTrue(doc.contains("/test/doc/example.xdef"));
                  } //
        );
        assertDoc("""
                          <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <ch<caret>ild name="Child"/>
                          </example>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("This is child node"));
                      assertTrue(doc.contains("/test/doc/example.xdef"));
                  } //
        );
        assertDoc("""
                          <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <abc-<caret>def name="Abc Def"/>
                          </example>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("Any child node"));
                      assertTrue(doc.contains("/test/doc/example.xdef"));
                  } //
        );
    }

    public void testGenerateDocForAttribute() {
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "xmlns:meta", //
                                   "xmlns:me<caret>ta"), //
                  TestCase::assertNull //
        );

        // xdef.xdef 中的 meta:xxx 属性显示相应的 xdef:xxx 属性文档
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "meta:check-ns=\"xdef\"", //
                                   "meta:che<caret>ck-ns=\"xdef\""), //
                  (doc) -> {
                      assertTrue(doc.contains("必须要校验的名字空间"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<meta:unknown-tag meta:ref=\"XDefNode\"/>", //
                                   "<meta:unknown-tag meta:re<caret>f=\"XDefNode\"/>"), //
                  (doc) -> {
                      assertTrue(doc.contains("引用本文件中"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:unknown-tag meta:ref=\"XDefNode\"/>", //
                                   "<xdef:unknown-tag m<caret>eta:ref=\"XDefNode\"/>"), //
                  (doc) -> {
                      assertTrue(doc.contains("引用本文件中"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "meta:unknown-attr=\"!xdef-attr\"", //
                                   "meta:unknown<caret>-attr=\"!xdef-attr\""), //
                  (doc) -> {
                      assertTrue(doc.contains("具有未明确定义"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "meta:unknown-attr=\"string\"", //
                                   "meta:unknown<caret>-attr=\"string\""), //
                  (doc) -> {
                      assertTrue(doc.contains("具有未明确定义"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:pre-parse meta:value", //
                                   "<xdef:pre-parse me<caret>ta:value"), //
                  (doc) -> {
                      assertTrue(doc.contains("body的数据类型"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );

        // *.xdef 中，xdef/x/xpl 名字空间的属性，始终显示其定义的文档
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "xdef:check-ns=\"word-set\"", //
                                   "xdef:chec<caret>k-ns=\"word-set\""), //
                  (doc) -> {
                      assertTrue(doc.contains("必须要校验的名字空间"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );

        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "xdef:name=\"var-name\"", //
                                   "xdef:na<caret>me=\"var-name\""), //
                  (doc) -> {
                      assertTrue(doc.contains("注册为xdef片段"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        // - Note: xdef:define 节点上的 xdef:name 属性与 meta:define 节点上的 xdef:name 共享文档
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:define xdef:name=\"!var-name\"", //
                                   "<xdef:define x<caret>def:name=\"!var-name\""), //
                  (doc) -> {
                      assertTrue(doc.contains("注册为xdef片段"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );

        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "xdef:check-ns=\"x\"", //
                                   "xdef:ch<caret>eck-ns=\"x\""), //
                  (doc) -> {
                      assertTrue(doc.contains("必须要校验的"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "xdef:allow-multiple=\"true\"", //
                                   "xde<caret>f:allow-multiple=\"true\""), //
                  (doc) -> {
                      assertTrue(doc.contains("允许多个实例"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );

        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "xdsl:schema", //
                                   "xdsl:sch<caret>ema"), //
                  (doc) -> {
                      assertTrue(doc.contains("元模型文件路径"));
                      assertTrue(doc.contains("/nop/schema/xdsl.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "x:schema", //
                                   "x:sch<caret>ema"), //
                  (doc) -> {
                      assertTrue(doc.contains("元模型文件路径"));
                      assertTrue(doc.contains("/nop/schema/xdsl.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/nop/schema/xdsl.xdef", //
                                   "x:key-attr=\"xml-name\"", //
                                   "x:key-<caret>attr=\"xml-name\""), //
                  (doc) -> {
                      assertTrue(doc.contains("子节点唯一属性"));
                      assertTrue(doc.contains("/nop/schema/xdsl.xdef"));
                  } //
        );

        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "<xdef:unknown-tag xdef:unknown-attr=\"any\"/>", //
                                   "<xdef:unknown-tag xdef:unknown<caret>-attr=\"any\"/>"), //
                  (doc) -> {
                      assertTrue(doc.contains("未明确定义"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "<refs xdef:value=\"v-path-list\"/>", //
                                   "<refs xd<caret>ef:value=\"v-path-list\"/>"), //
                  (doc) -> {
                      assertTrue(doc.contains("body的数据类型"));
                      assertTrue(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "x:dump=\"true\"", //
                                   "x:dum<caret>p=\"true\""), //
                  (doc) -> {
                      assertTrue(doc.contains("是否打印合并结果"));
                      assertTrue(doc.contains("/nop/schema/xdsl.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "xpl:dump=\"true\"", //
                                   "xpl:dum<caret>p=\"true\""), //
                  (doc) -> {
                      assertTrue(doc.contains("输出标签的AST树"));
                      assertTrue(doc.contains("/nop/schema/xpl.xdef"));
                  } //
        );

        assertDoc("""
                          <meta xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                          >
                              <xdef:pre-parse>
                                  <meta-gen:DefaultMetaGenExtends xpl:li<caret>b="/nop/core/xlib/meta-gen.xlib"/>
                              </xdef:pre-parse>
                          </meta>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("引入标签库"));
                      assertTrue(doc.contains("/nop/schema/xpl.xdef"));
                  } //
        );

        // *.xdef 中，非 xdef/x/xpl 名字空间的属性，显示其自身的文档
        assertDoc(insertCaretToVfs("/nop/schema/xdef.xdef", //
                                   "<xdef:prop name=\"!xml-name\"", //
                                   "<xdef:prop na<caret>me=\"!xml-name\""), //
                  (doc) -> {
                      assertFalse(doc.contains("具有未明确定义"));
                      assertFalse(doc.contains("/nop/schema/xdef.xdef"));
                  } //
        );
        assertDoc(insertCaretToVfs("/test/doc/example.xdef", //
                                   "<child name=\"string\"", //
                                   "<child na<caret>me=\"string\""), //
                  (doc) -> {
                      assertTrue(doc.contains("This is child name"));
                      assertFalse(doc.contains("/test/doc/example.xdef"));
                  } //
        );

        // 普通 dsl 中，显示属性定义的文档
        // - 明确的属性
        assertDoc("""
                          <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <child na<caret>me="Child"/>
                          </example>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("This is child name"));
                      assertTrue(doc.contains("/test/doc/example.xdef"));
                  } //
        );
        assertDoc("""
                          <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <child ty<caret>pe="leaf"/>
                          </example>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("dict:test/doc/child-type"));
                      assertTrue(doc.contains("/test/doc/example.xdef"));
                  } //
        );
        // - 未明确的属性
        assertDoc("""
                          <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <child ag<caret>e="22"/>
                          </example>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("This a unknown attribute"));
                      assertTrue(doc.contains("/test/doc/example.xdef"));
                  } //
        );

        assertDoc("""
                          <meta xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xmeta.xdef"
                          >
                              <x:gen-extends>
                                  <meta-gen:DefaultMetaGenExtends xpl:li<caret>b="/nop/core/xlib/meta-gen.xlib"/>
                              </x:gen-extends>
                          </meta>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("引入标签库"));
                      assertTrue(doc.contains("/nop/schema/xpl.xdef"));
                  } //
        );
        assertDoc("""
                          <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                             <tags>
                                 <GenXxx outputMode="node">
                                     <source>
                                         <meta-gen:DefaultMetaGenExtends xpl:li<caret>b="/nop/core/xlib/meta-gen.xlib"/>
                                     </source>
                                 </GenPage>
                             </tags>
                          </lib>
                          """, //
                  (doc) -> {
                      assertTrue(doc.contains("引入标签库"));
                      assertTrue(doc.contains("/nop/schema/xpl.xdef"));
                  } //
        );
    }

    public void testGenerateDocForXmlAttributeValue() {
        assertDoc("""
                          <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                              <child type="lea<caret>f"/>
                          </example>
                          """, "<p><b>leaf - Leaf Node</b></p>");
    }

    private void assertDoc(String text, String doc) {
        assertDoc(text, (genDoc) -> assertEquals(doc, genDoc));
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void assertDoc(String text, Consumer<String> checker) {
        configureByXLangText(text);

        String genDoc = getDocAtCaret();

        checker.accept(genDoc);
    }
}

