package io.nop.idea.plugin.reference;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.utils.XmlPsiHelper;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class TestXLangReferenceProvider extends BaseXLangPluginTestCase {

    public void testGetReferencesFromXmlAttributeValue() {
        // 对 v-path 属性值的引用
        // - x:schema=v-path
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/x<caret>meta.xdef"
                       />
                       """, "/nop/schema/xmeta.xdef");
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("x:schema=\"/nop/schema/xdef.xdef\"",
                                                                "x:schema=\"/nop/sche<caret>ma/xdef.xdef\""),
               "/nop/schema/xdef.xdef");
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema=\"/nop/schema/xdef.xdef\"",
                                                                "xdsl:schema=\"/nop/sche<caret>ma/xdef.xdef\""),
               "/nop/schema/xdef.xdef");
        // - xdef:default-extends=v-path
        doTest("""
                       <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xdef.xdef"
                             xdef:default-extends="/test/reference/de<caret>fault.xform"
                       />
                       """, "/test/reference/default.xform");
        // - xpl:lib=v-path
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xmeta.xdef"
                       >
                           <x:gen-extends>
                               <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/core/<caret>xlib/meta-gen.xlib"/>
                           </x:gen-extends>
                       </meta>
                       """, "/nop/core/xlib/meta-gen.xlib");
        // 对 v-path-list 列表元素的引用
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                             x:extends="/test/reference/a<caret>.xmeta,/test/reference/b.xmeta"
                       />
                       """, "/test/reference/a.xmeta");
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                             x:extends="/test/reference/a.xmeta,/test/reference/b.x<caret>meta"
                       />
                       """, "/test/reference/b.xmeta");

        // 对 xdef-ref 类型属性的引用
        // - 在 *.xdef 中引用内部名字
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:ref=\"XDefNode\"",
                                                                "meta:ref=\"XDe<caret>fNode\""),
               "meta:define#meta:name=XDefNode");
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdef:ref=\"DslNode\"", "xdef:ref=\"Dsl<caret>Node\""),
               "xdef:unknown-tag#xdef:name=DslNode");
//        // - 引用文件的相对路径出现在开头
//        doTest(readVfsResource("/nop/schema/xui/simple-component.xdef").replace("xdef:ref=\"../xui/import.xdef\"",
//                                                                                "xdef:ref=\"../xui/<caret>import.xdef\""),
//               "/nop/schema/xui/import.xdef");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                               x:schema="/nop/schema/xdef.xdef"
                       >
                            <xdef:define xdef:name="PropNode"/>
                            <prop name="string" xdef:ref="PropN<caret>ode"/>
                       </example>
                       """, "xdef:define#xdef:name=PropNode");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                               x:schema="/nop/schema/xdef.xdef"
                       >
                            <xdef:define xdef:name="PropNode"/>
                            <prop name="string" xdef:ref="Prop<caret>Node1"/>
                       </example>
                       """, null);
        // - 在 *.xdef 中引用外部文件
        doTest("""
                       <meta xmlns:x="/nop/sch<caret>ema/xdsl.xdef"
                             x:schema="/nop/schema/xmeta.xdef"
                       />
                       """, "/nop/schema/xdsl.xdef");
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                             x:schema="/nop/schema/xdef.xdef"
                             xdef:ref="/nop/schema/sch<caret>ema/obj-schema.xdef"
                       />
                       """, "/nop/schema/schema/obj-schema.xdef");
        // - 在 *.xmeta 中引用外部文件中的节点
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xmeta.xdef"
                             ref="/test/reference/test-filter.xdef<caret>#FilterCondition"
                       />
                       """, "xdef:define#xdef:name=FilterCondition");
        // - 外部文件中的引用节点不存在
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xmeta.xdef"
                             ref="/test/reference/test-filter.xdef<caret>#FilterCondition1"
                       />
                       """, null);

        // 对 x:prototype 属性值的引用
        doTest(readVfsResource("/test/reference/user.view.xml").replace("x:prototype=\"list\"",
                                                                        "x:prototype=\"li<caret>st\""), "grid#id=list");
        doTest(readVfsResource("/test/reference/a.xlib").replace("x:prototype=\"Get\"", "x:prototype=\"G<caret>et\""),
               "Get");
        // - 引用不存在
        doTest("""
                       <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                           <grids>
                               <grid id="pick-list" x:prototype="li<caret>st1"/>
                           </grids>
                       </view>
                       """, null);
        doTest("""
                       <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                           <tags>
                               <NewGet x:prototype="G<caret>ot"/>
                           </tags>
                       </lib>
                       """, null);

        // 对唯一键的引用
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                "meta:unique-attr=\"n<caret>ame\""),
               "xdef:prop#name=!xml-name");
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"xdef:name\"",
                                                                "meta:unique-attr=\"xdef:<caret>name\""),
               "xdef:define#xdef:name=!var-name");
        doTest(readVfsResource("/nop/schema/xmeta.xdef").replace("xdef:key-attr=\"id\"", "xdef:key-attr=\"i<caret>d\""),
               "selection#id=!var-name");
        // - 引用不存在
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                               x:schema="/nop/schema/xdef.xdef"
                       >
                            <prop name="string" xdef:unique-attr="i<caret>d"/>
                       </example>
                       """, null);
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                               x:schema="/nop/schema/xdef.xdef"
                       >
                            <props xdef:body-type="list" xdef:key-attr="i<caret>d">
                                <prop name="string"/>
                            </props>
                       </example>
                       """, null);

        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xdef.xdef"
                       >
                           <xdef:pre-parse>
                               <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/core/<caret>xlib/meta-gen.xlib"/>
                           </xdef:pre-parse>
                       </meta>
                       """, "/nop/core/xlib/meta-gen.xlib");
        doTest("""
                       <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                          <tags>
                              <GenXxx outputMode="node">
                                  <source>
                                      <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/<caret>core/xlib/meta-gen.xlib"/>
                                  </source>
                              </GenPage>
                          </tags>
                       </lib>
                       """, "/nop/core/xlib/meta-gen.xlib");

        // 非有效路径或未定义属性引用
        doTest(readVfsResource("/test/reference/user.view.xml").replace("xmlns:view-gen=\"view-gen\"",
                                                                        "xmlns:view-gen=\"vie<caret>w-gen\""), null);
        doTest("""
                       <dialog page="/test/reference/de<caret>fault.xform" />
                       """, null);

        // 未知 schema 导致引用无法识别，但支持对 *.xdef 的引用识别
        doTest("""
                       <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/sche<caret>ma/xform.xdef"/>
                       """, null);
        doTest("""
                       <form xmlns:x="/nop/schema/xd<caret>sl.xdef" x:schema="/nop/schema/xform.xdef"/>
                       """, "/nop/schema/xdsl.xdef");

//        // TODO 对 xpl 属性的文件引用
//        doTest("""
//                       <c:import from="/test/reference/a.x<caret>lib" />
//                       """, "/test/reference/a.xlib");
//        doTest("""
//                       <c:include src="/test/<caret>reference/a.xlib" />
//                       """, "/test/reference/a.xlib");
    }

    public void testGetReferencesFromXmlAttributeType() {
        // 声明属性将 引用 属性的类型定义
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                       >
                           <node type="vue-n<caret>ode"/>
                       </example>
                       """, "io.nop.xui.initialize.VueNodeStdDomainHandler");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                       >
                           <node type="x<caret>json"/>
                       </example>
                       """, "io.nop.xlang.xdef.domain.XJsonDomainHandler");

        // 字典/枚举的 options 引用
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                       >
                           <child type="dict:test/doc/ch<caret>ild-type"/>
                       </example>
                       """, "/dict/test/doc/child-type.dict.yaml");
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace(
                       "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"",
                       "x:override=\"enum:io.nop.xlang.xdef.X<caret>DefOverride=merge\""), //
               "io.nop.xlang.xdef.XDefOverride");

        // 字典/枚举的默认值引用
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                       >
                           <child type="dict:test/doc/child-type=le<caret>af"/>
                       </example>
                       """, "/dict/test/doc/child-type.dict.yaml#leaf");
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace(
                       "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"",
                       "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=me<caret>rge\""), //
               "io.nop.xlang.xdef.XDefOverride#MERGE");
//
//        // TODO 缺省属性值中 @attr: 引用
//        doTest("""
//                       <component xmlns:x="/nop/schema/xdsl.xdef"
//                                x:schema="/nop/schema/xdef.xdef"
//                       >
//                           <import as="!var-name=@attr:n<caret>ame" name="var-name" from="!string"/>
//                       </example>
//                       """, "");
//        doTest("""
//                       <component xmlns:x="/nop/schema/xdsl.xdef"
//                                x:schema="/nop/schema/xdef.xdef"
//                       >
//                           <var as="!var-name=@attr:name,ty<caret>pe" name="var-name" type="!string"/>
//                       </example>
//                       """, "");
    }

    public void testGetReferencesFromXmlAttribute() {
        // 名字空间不做引用
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                "me<caret>ta:unique-attr=\"name\""), null);

        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                "meta:unique<caret>-attr=\"name\""),
               "meta:define#xdef:unique-attr=xml-name");
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:prop name=\"!xml-name\"",
                                                                "<xdef:prop na<caret>me=\"!xml-name\""),
               "xdef:prop#name=!xml-name");
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:define xdef:name=\"!var-name\"",
                                                                "<xdef:define xdef:n<caret>ame=\"!var-name\""),
               "xdef:define#xdef:name=!var-name");

        doTest(readVfsResource("/test/doc/example.xdef").replace("name=\"string\"", "na<caret>me=\"string\""),
               "meta:define#xdef:unknown-attr=def-type");

        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <child ty<caret>pe="leaf"/>
                       </example>
                       """, "child#type=dict:test/doc/child-type");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <child a<caret>ge="22"/>
                       </example>
                       """, "child#xdef:unknown-attr=any");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <child2 a<caret>ge="23"/>
                       </example>
                       """, "xdef:unknown-tag#xdef:unknown-attr=any");

        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xmeta.xdef"
                             re<caret>f="/test/reference/test-filter.xdef#FilterCondition"
                       />
                       """, "schema#ref=xdef-ref");
    }

    public void testGetReferencesFromXmlText() {
        doTest(readVfsResource("/test/reference/user.view.xml").replace("<objMeta>", "<objMeta><caret>"),
               "/test/reference/a.xmeta");

        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <refs>/test/reference/test<caret>-filter.xdef,/nop/schema/xdsl.xdef</refs>
                       </example>
                       """, "/test/reference/test-filter.xdef");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <refs>
                                /test/reference/test-filter.xdef,/nop/schema/x<caret>dsl.xdef
                           </refs>
                       </example>
                       """, "/nop/schema/xdsl.xdef");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <refs><![CDATA[
                                /test/reference/tes<caret>t-filter.xdef, /nop/schema/xdsl.xdef
                           ]]></refs>
                       </example>
                       """, "/test/reference/test-filter.xdef");
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/doc/example.xdef"
                       >
                           <refs><![CDATA[
                                /test/reference/test-filter.xdef,
                                  /nop/schema<caret>/xdsl.xdef
                           ]]></refs>
                       </example>
                       """, "/nop/schema/xdsl.xdef");
    }

    public void testGetReferencesFromXmlTag() {
        doTest("""
                       <view xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/xui/xview.xdef"
                             xmlns:a="a" xmlns:xpl="xpl"
                       >
                             <x:gen-extends>
                                 <a:DefaultView<caret>GenExtends xpl:lib="/test/reference/a.xlib"/>
                             </x:gen-extends>
                       </view>
                       """, "");
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text, String expected) {
        configureByXLangText(text);

        PsiReference ref = findReferenceAtCaret();

        if (!(ref instanceof XLangReference) && expected == null) {
            return; // 不检查非 XLang 引用
        }
        assertInstanceOf(ref, XLangReference.class);

        PsiElement target = ref.resolve();
        if (expected == null) {
            assertNull(target);
            return;
        }
        assertNotNull(target);

        if (ref instanceof XLangVfsFileReference) {
            // Note: 可能不是 vfs 文件
            String vfsPath = XmlPsiHelper.getNopVfsPath(target);
            String anchor = target instanceof XmlAttribute attr ? attr.getValue() : null;

            assertEquals(expected, (vfsPath != null ? vfsPath : "") + (anchor != null ? "#" + anchor : ""));
        } //
        else if (ref instanceof XLangElementReference || ref instanceof XLangXDefReference) {
            if (target instanceof XmlTag tag) {
                assertEquals(expected, tag.getName());
            }  //
            else if (target instanceof XmlAttribute attr) {
                XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);

                assertEquals(expected, tag.getName() + "#" + attr.getName() + "=" + attr.getValue());
            } //
            else if (target instanceof PsiClass cls) {
                assertEquals(expected, cls.getQualifiedName());
            } //
            else if (target instanceof PsiField field) {
                assertEquals(expected, field.getContainingClass().getQualifiedName() + "#" + field.getName());
            } //
            else if (target instanceof PsiPlainText txt) {
                String vfsPath = XmlPsiHelper.getNopVfsPath(target);

                assertEquals(expected, vfsPath + ":" + txt.getTextOffset());
            } //
            else if (target instanceof LeafPsiElement leaf) {
                String vfsPath = XmlPsiHelper.getNopVfsPath(target);

                assertEquals(expected, vfsPath + "#" + leaf.getText());
            } //
            else {
                fail("Unknown target " + target.getClass());
            }
        }
    }
}
