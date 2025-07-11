package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.utils.XmlPsiHelper;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class TestXLangReferences extends BaseXLangPluginTestCase {

    public void testTagReferences() {
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xui/xview.xdef"
                                      xmlns:a="a" xmlns:xpl="xpl"
                                >
                                      <x:gen-extends>
                                          <a:DoFind<caret>ByMdxQuery xpl:lib="/test/reference/a.xlib"/>
                                      </x:gen-extends>
                                </view>
                                """, "/test/reference/a.xlib#DoFindByMdxQuery");
    }

    public void testAttributeReferences() {
        // xdef.xdef 中的引用识别
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                         "me<caret>ta:unique-attr=\"name\""), "meta");
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                         "meta:unique<caret>-attr=\"name\""),
                        "/nop/schema/xdef.xdef?meta:define#xdef:unique-attr=xml-name");

        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:prop name=\"!xml-name\"",
                                                                         "<xdef:prop na<caret>me=\"!xml-name\""),
                        "xdef:prop#name=!xml-name");
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:define xdef:name=\"!var-name\"",
                                                                         "<xdef:define xd<caret>ef:name=\"!var-name\""),
                        "xdef");
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("<xdef:define xdef:name=\"!var-name\"",
                                                                         "<xdef:define xdef:n<caret>ame=\"!var-name\""),
                        "xdef:define#xdef:name=!var-name");
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("<meta:define meta:name=\"XDefNode\"",
                                                                         "<meta:define meta:na<caret>me=\"XDefNode\""),
                        "/nop/schema/xdef.xdef?meta:define#xdef:name=var-name");

        // xdsl.xdef 中的引用识别
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema=", "xdsl:sch<caret>ema="),
                        "/nop/schema/xdsl.xdef?xdef:unknown-tag#x:schema=v-path");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace("x:schema=\"v-path\"",
                                                                         "x:sch<caret>ema=\"v-path\""),
                        "xdef:unknown-tag#x:schema=v-path");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace("xdef:allow-multiple=\"true\"",
                                                                         "xdef:allow-<caret>multiple=\"true\""),
                        "/nop/schema/xdef.xdef?meta:define#xdef:allow-multiple=boolean");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace("x:key-attr=\"xml-name\"",
                                                                         "x:key<caret>-attr=\"xml-name\""),
                        "xdef:unknown-tag#x:key-attr=xml-name");

        //
        assertReference(readVfsResource("/test/doc/example.xdef").replace("<child name=\"string\"",
                                                                          "<child na<caret>me=\"string\""),
                        "child#name=string");

        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child ty<caret>pe="leaf"/>
                                </example>
                                """, "/test/doc/example.xdef?child#type=dict:test/doc/child-type=node");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child a<caret>ge="22"/>
                                </example>
                                """, "/test/doc/example.xdef?child#xdef:unknown-attr=any");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child2 a<caret>ge="23"/>
                                </example>
                                """, "/test/doc/example.xdef?xdef:unknown-tag#xdef:unknown-attr=any");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <var some<caret>="aaa"/>
                                </example>
                                """, null);

        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                      re<caret>f="/test/reference/test-filter.xdef#FilterCondition"
                                />
                                """, "/nop/schema/schema/schema-node.xdef?schema#ref=xdef-ref");

        // 对 Xpl 属性的引用识别
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <div xpl:output<caret>Mode="node"/>
                                    </x:gen-extends>
                                </meta>
                                """,
                        "/nop/schema/xpl.xdef?xdef:define#xpl:outputMode=enum:io.nop.xlang.ast.XLangOutputMode");
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <div xpl:outputMode="node">
                                            <xpl:decorator xpl:i<caret>f="true"/>
                                        </div>
                                    </x:gen-extends>
                                </meta>
                                """, null);
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <div xpl:outputMode="node">
                                            <xpl:decorator>
                                               <windowing xpl:i<caret>f="true"/>
                                            </xpl:decorator>
                                        </div>
                                    </x:gen-extends>
                                </meta>
                                """, "/nop/schema/xpl.xdef?xdef:define#xpl:if=expr");
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <Get>
                                            <source>
                                                <http-get xpl:i<caret>f="a > b"/>
                                            </source>
                                        </Get>
                                    </tags>
                                </lib>
                                """, "/nop/schema/xpl.xdef?xdef:define#xpl:if=expr");
    }

    public void testAttributeValueReferences() {
        // 对 v-path 属性值的引用
        // - x:schema=v-path
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/x<caret>meta.xdef"
                                />
                                """, "/nop/schema/xmeta.xdef");
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("x:schema=\"/nop/schema/xdef.xdef\"",
                                                                         "x:schema=\"/nop/sche<caret>ma/xdef.xdef\""),
                        "/nop/schema/xdef.xdef");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace("xdsl:schema=\"/nop/schema/xdef.xdef\"",
                                                                         "xdsl:schema=\"/nop/sche<caret>ma/xdef.xdef\""),
                        "/nop/schema/xdef.xdef");
        // - xdef:default-extends=v-path
        assertReference("""
                                <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xdef.xdef"
                                      xdef:default-extends="/test/reference/de<caret>fault.xform"
                                />
                                """, "/test/reference/default.xform");
        // - xpl:lib=v-path
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/core/<caret>xlib/meta-gen.xlib"/>
                                    </x:gen-extends>
                                </meta>
                                """, "/nop/core/xlib/meta-gen.xlib");
        // 对 v-path-list 列表元素的引用
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                                      x:extends="/test/reference/a<caret>.xmeta,/test/reference/b.xmeta"
                                />
                                """, "/test/reference/a.xmeta");
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                                      x:extends="/test/reference/a.xmeta,/test/reference/b.x<caret>meta"
                                />
                                """, "/test/reference/b.xmeta");

        // 对 xdef-ref 类型属性的引用
        // - 在 *.xdef 中引用内部名字
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("meta:ref=\"XDefNode\"",
                                                                         "meta:ref=\"XDe<caret>fNode\""),
                        "meta:define#meta:name=XDefNode");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace("xdef:ref=\"DslNode\"",
                                                                         "xdef:ref=\"Dsl<caret>Node\""),
                        "xdef:unknown-tag#xdef:name=DslNode");
//        // - 引用文件的相对路径出现在开头
//        doTest(readVfsResource("/nop/schema/xui/simple-component.xdef").replace("xdef:ref=\"../xui/import.xdef\"",
//                                                                                "xdef:ref=\"../xui/<caret>import.xdef\""),
//               "/nop/schema/xui/import.xdef");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <xdef:define xdef:name="PropNode"/>
                                     <prop name="string" xdef:ref="PropN<caret>ode"/>
                                </example>
                                """, "xdef:define#xdef:name=PropNode");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <xdef:define xdef:name="PropNode"/>
                                     <prop name="string" xdef:ref="Prop<caret>Node1"/>
                                </example>
                                """, null);
        // - 在 *.xdef 中引用外部文件
        assertReference("""
                                <meta xmlns:x="/nop/sch<caret>ema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                />
                                """, "/nop/schema/xdsl.xdef");
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                      x:schema="/nop/schema/xdef.xdef"
                                      xdef:ref="/nop/schema/sch<caret>ema/obj-schema.xdef"
                                />
                                """, "/nop/schema/schema/obj-schema.xdef");
        // - 在 *.xmeta 中引用外部文件中的节点
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                      ref="/test/reference/test-filter.xdef<caret>#FilterCondition"
                                />
                                """, "xdef:define#xdef:name=FilterCondition");
        // - 外部文件中的引用节点不存在
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                      ref="/test/reference/test-filter.xdef<caret>#FilterCondition1"
                                />
                                """, null);

        // 对 x:prototype 属性值的引用
        assertReference(readVfsResource("/test/reference/user.view.xml").replace("x:prototype=\"list\"",
                                                                                 "x:prototype=\"li<caret>st\""),
                        "grid#id=list");
        assertReference(readVfsResource("/test/reference/a.xlib").replace("x:prototype=\"Get\"",
                                                                          "x:prototype=\"G<caret>et\""), "Get");
        // - 引用不存在
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                    <grids>
                                        <grid id="pick-list" x:prototype="li<caret>st1"/>
                                    </grids>
                                </view>
                                """, null);
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <NewGet x:prototype="G<caret>ot"/>
                                    </tags>
                                </lib>
                                """, null);

        // 对唯一键的引用
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"name\"",
                                                                         "meta:unique-attr=\"n<caret>ame\""),
                        "xdef:prop#name=!xml-name");
        assertReference(readVfsResource("/nop/schema/xdef.xdef").replace("meta:unique-attr=\"xdef:name\"",
                                                                         "meta:unique-attr=\"xdef:<caret>name\""),
                        "xdef:define#xdef:name=!var-name");
        assertReference(readVfsResource("/nop/schema/xmeta.xdef").replace("xdef:key-attr=\"id\"",
                                                                          "xdef:key-attr=\"i<caret>d\""),
                        "selection#id=!var-name");
        // - 引用不存在
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop name="string" xdef:unique-attr="i<caret>d"/>
                                </example>
                                """, null);
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <props xdef:body-type="list" xdef:key-attr="i<caret>d">
                                         <prop name="string"/>
                                     </props>
                                </example>
                                """, null);

        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xdef.xdef"
                                >
                                    <xdef:pre-parse>
                                        <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/core/<caret>xlib/meta-gen.xlib"/>
                                    </xdef:pre-parse>
                                </meta>
                                """, "/nop/core/xlib/meta-gen.xlib");
        assertReference("""
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
        assertReference(readVfsResource("/test/reference/user.view.xml").replace("xmlns:view-gen=\"view-gen\"",
                                                                                 "xmlns:view-gen=\"vie<caret>w-gen\""),
                        null);
        assertReference("""
                                <dialog page="/test/reference/de<caret>fault.xform" />
                                """, null);

        // 未知 schema 导致引用无法识别，但支持对 *.xdef 的引用识别
        assertReference("""
                                <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/sche<caret>ma/xform.xdef"/>
                                """, null);
        assertReference("""
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

    public void testAttributeTypeReferences() {
        // 声明属性将 引用 属性的类型定义
        // TODO 暂时无法通过分析 class 字节码得到可注册的数据域
//        // - #getName 返回引用值
//        doTest("""
//                       <example xmlns:x="/nop/schema/xdsl.xdef"
//                                x:schema="/nop/schema/xdef.xdef"
//                       >
//                           <node type="string"/>
//                       </example>
//                       """, "/dict/test/doc/child-type.dict.yaml#leaf");
//        // - #getName 返回字面量值
//        doTest("""
//                       <example xmlns:x="/nop/schema/xdsl.xdef"
//                                x:schema="/nop/schema/xdef.xdef"
//                       >
//                           <node type="x<caret>json"/>
//                       </example>
//                       """, "io.nop.xlang.xdef.domain.XJsonDomainHandler");
        // - 引用字典中定义的数据域
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <node type="str<caret>ing"/>
                                </example>
                                """, "/dict/core/std-domain.dict.yaml#string");

        // 字典/枚举的 options 引用
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <child type="dict:test/doc/ch<caret>ild-type"/>
                                </example>
                                """, "/dict/test/doc/child-type.dict.yaml");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace(
                                "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"",
                                "x:override=\"enum:io.nop.xlang.xdef.X<caret>DefOverride=merge\""), //
                        "io.nop.xlang.xdef.XDefOverride");

        // 字典/枚举的默认值引用
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <child type="dict:test/doc/child-type=le<caret>af"/>
                                </example>
                                """, "/dict/test/doc/child-type.dict.yaml#leaf");
        assertReference(readVfsResource("/nop/schema/xdsl.xdef").replace(
                                "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"",
                                "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=me<caret>rge\""), //
                        "io.nop.xlang.xdef.XDefOverride#MERGE");

        // 缺省属性值中 @attr: 引用
        assertReference("""
                                <component xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <import as="!var-name=@attr:n<caret>ame" name="var-name" from="!string"/>
                                </example>
                                """, "import#name=var-name");
        assertReference("""
                                <component xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <var com="!var-name=@attr:name,ty<caret>pe" name="var-name" type="!string"/>
                                </example>
                                """, "var#type=!string");
        assertReference("""
                                <component xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <var com="!var-name=@attr:ab<caret>c"/>
                                </example>
                                """, null);
    }

    public void testTextReferences() {
        assertReference(readVfsResource("/test/reference/user.view.xml").replace("<objMeta>", "<objMeta><caret>"),
                        "/test/reference/a.xmeta");

        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs>/test/reference/test<caret>-filter.xdef,/nop/schema/xdsl.xdef</refs>
                                </example>
                                """, "/test/reference/test-filter.xdef");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs>
                                         /test/reference/test-filter.xdef,/nop/schema/x<caret>dsl.xdef
                                    </refs>
                                </example>
                                """, "/nop/schema/xdsl.xdef");
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs><![CDATA[
                                         /test/reference/tes<caret>t-filter.xdef, /nop/schema/xdsl.xdef
                                    ]]></refs>
                                </example>
                                """, "/test/reference/test-filter.xdef");
        assertReference("""
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

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void assertReference(String text, String expected) {
        configureByXLangText(text);

        PsiReference ref = findReferenceAtCaret();
        PsiElement target = ref != null ? ref.resolve() : null;

        if (expected == null) {
            assertNull(target);
            return;
        }
        assertNotNull(target);

        // Note: 可能不是 vfs 文件
        String vfsPath = XmlPsiHelper.getNopVfsPath(target);

        if (target instanceof XmlAttribute attr) {
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);

            assertEquals(expected,
                         (vfsPath != null ? vfsPath + '?' : "")
                         + tag.getName()
                         + '#'
                         + attr.getName()
                         + '='
                         + attr.getValue());
        } else if (target instanceof SchemaPrefix ns) {
            assertEquals(expected, ns.getName());
        }

//        if (ref instanceof XLangVfsFileReference) {
//            // Note: 可能不是 vfs 文件
//            String vfsPath = XmlPsiHelper.getNopVfsPath(target);
//            String anchor = target instanceof XmlAttribute attr ? attr.getValue() : null;
//
//            assertEquals(expected, (vfsPath != null ? vfsPath : "") + (anchor != null ? "#" + anchor : ""));
//        } //
//        else if (ref instanceof XLangElementReference || ref instanceof XLangXDefReference) {
//            if (target instanceof XmlTag tag) {
//                assertEquals(expected, tag.getName());
//            }  //
//            else if (target instanceof XmlAttribute attr) {
//                XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
//
//                assertEquals(expected, tag.getName() + "#" + attr.getName() + "=" + attr.getValue());
//            } //
//            else if (target instanceof PsiClass cls) {
//                assertEquals(expected, cls.getQualifiedName());
//            } //
//            else if (target instanceof PsiField field) {
//                assertEquals(expected, field.getContainingClass().getQualifiedName() + "#" + field.getName());
//            } //
//            else if (target instanceof PsiPlainText txt) {
//                String vfsPath = XmlPsiHelper.getNopVfsPath(target);
//
//                assertEquals(expected, vfsPath + ":" + txt.getTextOffset());
//            } //
//            else if (target instanceof LeafPsiElement leaf) {
//                String vfsPath = XmlPsiHelper.getNopVfsPath(target);
//
//                assertEquals(expected, vfsPath + "#" + leaf.getText());
//            } //
//            else {
//                fail("Unknown target " + target.getClass());
//            }
//        }
    }
}
