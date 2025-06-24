package io.nop.idea.plugin.link;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.nop.idea.plugin.BaseXLangPluginTestCase;

/**
 * 参考 https://github.com/JetBrains/intellij-community/blob/master/plugins/groovy/test/org/jetbrains/plugins/groovy/GroovyGoToTypeDeclarationTest.java
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-17
 */
public class TestXLangGotoDeclarationHandler extends BaseXLangPluginTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Note: 提前将需要跳转的文件添加到 Project 中
        addVfsResourcesToProject("/nop/schema/xdef.xdef",
                                 "/nop/schema/xdsl.xdef",
                                 "/nop/schema/xmeta.xdef",
                                 "/nop/schema/xui/xview.xdef",
                                 "/nop/schema/xui/store.xdef",
                                 "/nop/core/xlib/meta-gen.xlib",
                                 "/nop/schema/schema/obj-schema.xdef",
                                 "/dict/test/doc/child-type.dict.yaml",
                                 "/test/link/a.xmeta",
                                 "/test/link/b.xmeta",
                                 "/test/link/a.xlib",
                                 "/test/link/default.xform",
                                 "/test/link/test-filter.xdef");
    }

    public void testGetGotoDeclarationTargetsForXmlTag() {
    }

    public void testGetGotoDeclarationTargetsForXmlAttributeValue() {
        // 根据在 schema 中定义的属性类型决定跳转
        // - x:extends=v-path-list
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                             x:extends="/test/link/a<caret>.xmeta"
                       />
                       """, "/test/link/a.xmeta");

        // 对 xdef.xdef 中引用的跳转
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("x:schema=\"/nop/schema/xdef.xdef\"",
                                                                "x:schema=\"/nop/sche<caret>ma/xdef.xdef\""),
               "/nop/schema/xdef.xdef");

        // 对 xdsl.xdef 中引用的跳转
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema=\"/nop/schema/xdef.xdef\"",
                                                                "xdsl:schema=\"/nop/sche<caret>ma/xdef.xdef\""),
               "/nop/schema/xdef.xdef");

        // 对 xdef-ref 类型属性的跳转
        // - 在 *.xdef 中引用内部名字
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:ref=\"XDefNode\"",
                                                                "meta:ref=\"XDe<caret>fNode\""), "XDefNode");
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("xdef:ref=\"DslNode\"", "xdef:ref=\"Dsl<caret>Node\""),
               "DslNode");
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
                             ref="/test/link/test-filter.xdef<caret>#FilterCondition"
                       />
                       """, "FilterCondition");

        // 对 x:prototype 属性值的跳转
        doTest(readVfsResource("/test/link/user.view.xml").replace("x:prototype=\"list\"",
                                                                   "x:prototype=\"li<caret>st\""), "list");
        doTest(readVfsResource("/test/link/a.xlib").replace("x:prototype=\"Get\"", "x:prototype=\"G<caret>et\""),
               "Get");

        // 对唯一键的跳转
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                "meta:unique-attr=\"n<caret>ame\""), "name");
        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"xdef:name\"",
                                                                "meta:unique-attr=\"xdef:<caret>name\""), "xdef:name");
        doTest(readVfsResource("/nop/schema/xmeta.xdef").replace("xdef:key-attr=\"name\"",
                                                                 "xdef:key-attr=\"na<caret>me\""), "name");

        // 缺省：任意有效的文件均可跳转
        doTest("""
                       <c:import from="/test/link/a.x<caret>lib" />
                       """, "/test/link/a.xlib");
        doTest("""
                       <c:include src="/test/<caret>link/a.xlib" />
                       """, "/test/link/a.xlib");
        doTest("""
                       <dialog page="/test/link/de<caret>fault.xform" />
                       """, "/test/link/default.xform");
        // - x:schema=v-path
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef"
                             x:schema="/nop/schema/x<caret>meta.xdef"
                       />
                       """, "/nop/schema/xmeta.xdef");
        // - xdef:default-extends=v-path
        doTest("""
                       <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xdef.xdef"
                             xdef:default-extends="/test/link/de<caret>fault.xform"
                       />
                       """, "/test/link/default.xform");
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

//        // TODO 声明属性仅跳转到属性的类型定义上
//        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("xdef:ref=\"xdef-ref\"",
//                                                                "xdef:ref=\"xd<caret>ef-ref\""), "");
//        doTest(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:prop name=\"!xml-name\"",
//                                                                "<xdef:prop name=\"!xml<caret>-name\""), "");
//        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace("x:schema=\"v-path\"", "x:schema=\"v-pa<caret>th\""),
//               "");
    }

    public void testGetGotoDeclarationTargetsForXmlAttributePartialValue() {
        // v-path-list 元素跳转
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                             x:extends="/test/link/a.xmeta,/test/link/b<caret>.xmeta"
                       />
                       """, "/test/link/b.xmeta");
        doTest("""
                       <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                             x:extends="/test/link/a.xm<caret>eta,/test/link/b.xmeta"
                       />
                       """, "/test/link/a.xmeta");

        // TODO 字典/枚举的 options 跳转
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

        // TODO 字典/枚举的默认值跳转
        doTest("""
                       <example xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                       >
                           <child type="dict:test/doc/child-type=le<caret>af"/>
                       </example>
                       """, "");
        doTest(readVfsResource("/nop/schema/xdsl.xdef").replace(
                       "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"",
                       "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=me<caret>rge\""), //
               "");

        // TODO 缺省属性值中 @attr: 引用跳转
        doTest("""
                       <component xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                       >
                           <import as="!var-name=@attr<caret>:name" name="var-name" from="!string"/>
                       </example>
                       """, "");
    }

    public void testGetGotoDeclarationTargetsForXmlText() {
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void doTest(String text, String... expected) {
        configureByXLangText(text);

        PsiElement[] refs = getGotoTargets();
        assertNotNull(refs);
        assertEquals(refs.length, expected.length);

        for (int i = 0; i < refs.length; i++) {
            PsiElement ref = refs[i];
            String exp = expected[i];

            // 引用文件
            if (ref instanceof XmlFile) {
                String file = ((XmlFile) ref).getVirtualFile().toString();
                String actual = file.substring(file.indexOf("/_vfs/") + "/_vfs".length());

                assertEquals(exp, actual);
            }
            // 引用节点
            else if (ref instanceof XmlTag tag) {
                // Note: xdef.xdef 中的 meta:name 才是节点名
                String actual = tag.getAttributeValue("meta:name");
                if (actual == null) {
                    // 其他 *.xdef 中的节点名为 xdef:name
                    actual = tag.getAttributeValue("xdef:name");
                }

                if (actual == null) {
                    actual = tag.getAttributeValue("id");
                }

                // 缺省采用节点标签名
                if (actual == null) {
                    actual = tag.getName();
                }

                assertEquals(exp, actual);
            }
            // 引用属性
            else if (ref instanceof XmlAttribute attr) {
                String actual = attr.getName();
                assertEquals(exp, actual);
            }
        }
    }
}
