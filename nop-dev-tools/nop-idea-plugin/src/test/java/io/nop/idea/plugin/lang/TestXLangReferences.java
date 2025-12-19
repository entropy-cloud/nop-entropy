package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class TestXLangReferences extends BaseXLangPluginTestCase {

    public void testTagDefReferences() {
        // 名字空间保持名字引用
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "<xdef:pre-parse", //
                                           "<x<caret>def:pre-parse"), //
                        "xdef" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "</meta:define>", //
                                           "</me<caret>ta:define>"), //
                        "meta" //
        );

        // *.xdef 的根节点定义始终对应 xdef.xdef 的根节点 meta:unknown-tag，
        // 同时，在 xdef.xdef 中未定义的子节点也为对应的 meta:unknown-tag
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "<meta:unknown-tag x:schema", //
                                           "<meta:unkn<caret>own-tag x:schema"), //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "<xdef:unknown-tag xdsl:schema", //
                                           "<xdef:unk<caret>nown-tag xdsl:schema"), //
                        //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "<x:post-parse", //
                                           "<x:post<caret>-parse"), //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xpl.xdef", //
                                           "<xpl:decorator xdef:value=\"xpl\"/>", //
                                           "<xpl:decorator<caret> xdef:value=\"xpl\"/>"), //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                </exa<caret>mple>
                                """, //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );

        // xdef.xdef 中的节点交叉定义
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "<xdef:pre-parse", //
                                           "<xdef:pre-<caret>parse"), //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "<xdef:unknown-tag", //
                                           "<xdef:unknow<caret>n-tag"), //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "</meta:define>", //
                                           "</meta:de<caret>fine>"), //
                        "/nop/schema/xdef.xdef?xdef:define" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef",//
                                           "<meta:unknown-tag meta:ref=\"XDefNode\"/>", //
                                           "<meta:unkn<caret>own-tag meta:ref=\"XDefNode\"/>"), //
                        "/nop/schema/xdef.xdef?xdef:unknown-tag" //
        );

        // 对 xdef.xdef 中同名子节点的定义引用
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef",//
                                           "<xdef:unknown-tag xdef:ref=\"DslNode\"/>", //
                                           "<xdef:unkn<caret>own-tag xdef:ref=\"DslNode\"/>"), //
                        "/nop/schema/xdef.xdef?xdef:unknown-tag" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "<xdef:unknown-tag xdef:value", //
                                           "<xdef:unk<caret>nown-tag xdef:value"), //
                        "/nop/schema/xdef.xdef?xdef:unknown-tag" //
        );

        assertReference(insertCaretIntoVfs("/nop/schema/xpl.xdef", //
                                           "<xdef:define xdef:name", //
                                           "<xdef:def<caret>ine xdef:name"), //
                        "/nop/schema/xdef.xdef?xdef:define" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xpl.xdef",//
                                           "<xdef:unknown-tag xpl:unknown-attr", //
                                           "<xdef:unkn<caret>own-tag xpl:unknown-attr"), //
                        "/nop/schema/xdef.xdef?xdef:unknown-tag" //
        );

        // 普通 xdef 内的定义引用
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <xdef:post<caret>-parse/>
                                </example>
                                """, //
                        "/nop/schema/xdef.xdef?xdef:post-parse" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                         x:schema="/nop/schema/xdef.xdef"
                                >
                                    <chi<caret>ld name="string"/>
                                </example>
                                """, //
                        "/nop/schema/xdef.xdef?meta:unknown-tag" //
        );

        // DSL 内的定义引用
        assertReference("""
                                <exa<caret>mple xmlns:x="/nop/schema/xdsl.xdef"
                                           x:schema="/test/doc/example.xdef"
                                >
                                </example>
                                """, //
                        "/test/doc/example.xdef?example" //
        );
        assertReference("""
                                <exa<caret>mple xmlns:x="/nop/schema/xdsl.xdef"
                                           x:schema="/test/doc/example.xdef"
                                >
                                    <x:gen-<caret>extends/>
                                </example>
                                """, //
                        "/nop/schema/xdsl.xdef?x:gen-extends" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                           x:schema="/test/doc/example.xdef"
                                >
                                    <ch<caret>ild name="Child"/>
                                </example>
                                """, //
                        "/test/doc/example.xdef?child" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                           x:schema="/test/doc/example.xdef"
                                >
                                    <unkn<caret>own name="abc"/>
                                </example>
                                """, //
                        "/test/doc/example.xdef?xdef:unknown-tag" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                           x:schema="/test/doc/example.xdef"
                                >
                                    <tag-no-child>
                                        <ab<caret>c name="abc"/>
                                    </tag-no-child>
                                </example>
                                """, //
                        null //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                           x:schema="/test/doc/example.xdef"
                                >
                                    <tag-no-child>
                                        <x:gen-e<caret>xtends/>
                                    </tag-no-child>
                                </example>
                                """, //
                        "/nop/schema/xdsl.xdef?x:gen-extends" //
        );
    }

    public void testXplReferences() {
        // xlib 中的 source 标签内的引用
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <Call>
                                            <source></so<caret>urce>
                                        </Call>
                                    </tags>
                                </lib>
                                """, //
                        "/nop/schema/xlib.xdef?source" //
        );
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <Call>
                                            <source>
                                                <a<caret>bc xpl:if="true"/>
                                            </source>
                                        </Call>
                                    </tags>
                                </lib>
                                """, //
                        "/nop/schema/xpl.xdef?xdef:unknown-tag" //
        );

//        // TODO xpl 节点内引用内置的 xpl 标签函数
//        assertReference("""
//                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
//                                      <x:gen-extends>
//                                          <c:imp<caret>ort from="/test/reference/a.xlib"/>
//                                      </x:gen-extends>
//                                </view>
//                                """,  //
//                        "" //
//        );
//        assertReference("""
//                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
//                                      <x:gen-extends>
//                                          <c:script></c:scr<caret>ipt>
//                                      </x:gen-extends>
//                                </view>
//                                """,  //
//                        "" //
//        );

        // xlib 标签函数引用识别
        // - 通过 xpl:lib 导入 xlib
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <a:DoFind<caret>ByMdxQuery xpl:lib="/test/reference/a.xlib"/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "/test/reference/a.xlib?DoFindByMdxQuery" //
        );
        // - 通过 c:import 导入 xlib
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:import from="/test/reference/a.xlib"/>
                                          <a:DoFind<caret>ByMdxQuery/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "/test/reference/a.xlib?DoFindByMdxQuery" //
        );
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:import as="gen" from="/test/reference/a.xlib"/>
                                          <gen:DoFind<caret>ByMdxQuery/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "/test/reference/a.xlib?DoFindByMdxQuery" //
        );
        // - thisLib 函数的识别
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <Call>
                                            <source>
                                                <thisLib:_DoSo<caret>mething/>
                                            </source>
                                        </Call>
                                        <_DoSomething/>
                                    </tags>
                                </lib>
                                """, //
                        "_DoSomething" //
        );
        // - 名字空间引用识别
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <a<caret>:DoFindByMdxQuery xpl:lib="/test/reference/a.xlib"/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "a:DoFindByMdxQuery#xpl:lib=/test/reference/a.xlib" //
        );
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:import from="/test/reference/gen.xlib"/>
                                          <g<caret>en:DoSomething/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "c:import" //
        );
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <Call>
                                            <source>
                                                <thi<caret>sLib:_DoSomething/>
                                            </source>
                                        </Call>
                                        <_DoSomething/>
                                    </tags>
                                </lib>
                                """, //
                        "thisLib:_DoSomething" //
        );

        // - 标签函数中的参数识别
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <a:DoFindByMdxQuery met<caret>hod="post" xpl:lib="/test/reference/a.xlib"/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "/test/reference/a.xlib?attr#name=method" //
        );
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:import from="/test/reference/a.xlib"/>
                                          <a:DoFindByMdxQuery met<caret>hod="post"/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "/test/reference/a.xlib?attr#name=method" //
        );
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:import as="gen" from="/test/reference/a.xlib"/>
                                          <gen:DoFindByMdxQuery met<caret>hod="post"/>
                                      </x:gen-extends>
                                </view>
                                """, //
                        "/test/reference/a.xlib?attr#name=method" //
        );
    }

    public void testAttributeReferences() {
        // xdef.xdef 属性的交叉定义识别
        // - 名字空间引用其自身
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:unique-attr=\"name\"", //
                                           "me<caret>ta:unique-attr=\"name\""), //
                        "meta" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef",//
                                           "<xdef:define xdef:name=\"!var-name\"", //
                                           "<xdef:define xd<caret>ef:name=\"!var-name\""), //
                        "xdef" //
        );
        // - 以 meta 为名字空间的属性（含 meta:unknown-attr）由对应的 xdef:xxx 定义
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:unknown-attr=\"!xdef-attr\"", //
                                           "meta:unknown<caret>-attr=\"!xdef-attr\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:unknown-attr=def-type" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:bean-tag-prop=\"tagName\"", //
                                           "meta:bean-<caret>tag-prop=\"tagName\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:bean-tag-prop=prop-name" //
        );
        // - 全部以 xdef 为名字空间的属性均由 meta:unknown-attr 定义
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "xdef:unknown-attr=\"def-type\"", //
                                           "xdef:unknown<caret>-attr=\"def-type\""), //
                        "/nop/schema/xdef.xdef?meta:define#meta:unknown-attr=!xdef-attr" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "xdef:unique-attr=\"xml-name\"", //
                                           "xdef:unique<caret>-attr=\"xml-name\""), //
                        "/nop/schema/xdef.xdef?meta:define#meta:unknown-attr=!xdef-attr" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "xdef:ref=\"xdef-ref\"", //
                                           "xdef:r<caret>ef=\"xdef-ref\""), //
                        "/nop/schema/xdef.xdef?meta:define#meta:unknown-attr=!xdef-attr" //
        );
        // - xdef 或 meta 名字空间的节点属性，也满足以上规则
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef",//
                                           "<meta:unknown-tag meta:ref=\"XDefNode\"/>", //
                                           "<meta:unknown-tag meta:re<caret>f=\"XDefNode\"/>"), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:ref=xdef-ref" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef",//
                                           "<xdef:unknown-tag meta:ref=\"XDefNode\"/>", //
                                           "<xdef:unknown-tag meta:re<caret>f=\"XDefNode\"/>"), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:ref=xdef-ref" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "<xdef:prop name=\"!xml-name\"", //
                                           "<xdef:prop na<caret>me=\"!xml-name\""), //
                        "/nop/schema/xdef.xdef?meta:define#meta:unknown-attr=!xdef-attr" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:unknown-attr=\"string\"", //
                                           "meta:unkn<caret>own-attr=\"string\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:unknown-attr=def-type" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef",//
                                           "<xdef:define xdef:name=\"!var-name\"", //
                                           "<xdef:define xdef:n<caret>ame=\"!var-name\""), //
                        "/nop/schema/xdef.xdef?meta:define#meta:unknown-attr=!xdef-attr" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef",//
                                           "<meta:define meta:name=\"XDefNode\"", //
                                           "<meta:define meta:na<caret>me=\"XDefNode\""), //
                        "/nop/schema/xdef.xdef?xdef:define#xdef:name=!var-name" //
        );

        // xdsl.xdef 中的属性定义识别
        // - 交叉识别
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "xdsl:schema=", //
                                           "xdsl:sch<caret>ema="), //
                        "/nop/schema/xdsl.xdef?xdef:unknown-tag#x:schema=v-path" //
        );
        // - 普通定义
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "x:schema=\"v-path\"", //
                                           "x:sch<caret>ema=\"v-path\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:unknown-attr=def-type" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "xdef:allow-multiple=\"true\"", //
                                           "xdef:allow-<caret>multiple=\"true\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:allow-multiple=boolean" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "x:key-attr=\"xml-name\"", //
                                           "x:key<caret>-attr=\"xml-name\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:unknown-attr=def-type" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef",//
                                           "<x:gen-extends xdef:value=\"xpl-node\"/>", //
                                           "<x:gen-extends xdef:va<caret>lue=\"xpl-node\"/>"), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:value=def-type" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef",//
                                           "<x:super xdef:internal=\"true\"/>", //
                                           "<x:super xdef:int<caret>ernal=\"true\"/>"), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:internal=boolean" //
        );

        // 普通 xdef 的属性定义识别
        assertReference(insertCaretIntoVfs("/test/doc/example.xdef", //
                                           "<child name=\"string\"", //
                                           "<child na<caret>me=\"string\""), //
                        "/nop/schema/xdef.xdef?meta:define#xdef:unknown-attr=def-type" //
        );
        assertReference(insertCaretIntoVfs("/test/doc/example.xdef", //
                                           "x:dump", //
                                           "x:du<caret>mp"), //
                        "/nop/schema/xdsl.xdef?xdef:unknown-tag#x:dump=boolean" //
        );
        assertReference(insertCaretIntoVfs("/test/doc/example.xdef", //
                                           "xpl:dump", //
                                           "xpl:du<caret>mp"), //
                        "/nop/schema/xpl.xdef?xdef:define#xpl:dump=boolean" //
        );

        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child type="leaf" x:abst<caret>ract="true"/>
                                </example>
                                """,  //
                        "/nop/schema/xdsl.xdef?xdef:unknown-tag#x:abstract=boolean" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child ty<caret>pe="leaf"/>
                                </example>
                                """,  //
                        "/test/doc/example.xdef?child#type=dict:test/doc/child-type=node" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child a<caret>ge="22"/>
                                </example>
                                """,  //
                        "/test/doc/example.xdef?child#xdef:unknown-attr=any" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child2 a<caret>ge="23"/>
                                </example>
                                """,  //
                        "/test/doc/example.xdef?xdef:unknown-tag#xdef:unknown-attr=any" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <var some<caret>="aaa"/>
                                </example>
                                """, //
                        null //
        );

        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                      re<caret>f="/test/reference/test-filter.xdef#FilterCondition"
                                />
                                """,  //
                        "/nop/schema/schema/schema-node.xdef?schema#ref=xdef-ref" //
        );

        // 对 Xpl 属性的引用识别
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <meta-gen:DefaultMetaGenExtends xpl:du<caret>mp="true"/>
                                    </x:gen-extends>
                                </meta>
                                """,  //
                        "/nop/schema/xpl.xdef?xdef:define#xpl:dump=boolean" //
        );
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <div xpl:output<caret>Mode="node"/>
                                    </x:gen-extends>
                                </meta>
                                """,
                        "/nop/schema/xpl.xdef?xdef:define#xpl:outputMode=enum:io.nop.xlang.ast.XLangOutputMode"
                        //
        );
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
                                """, //
                        null //
        );
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
                                """,  //
                        "/nop/schema/xpl.xdef?xdef:define#xpl:if=expr" //
        );
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
                                """,  //
                        "/nop/schema/xpl.xdef?xdef:define#xpl:if=expr" //
        );
    }

    public void testAttributeValueReferences() {
        // 对 v-path 属性值的引用
        // - x:schema=v-path
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/x<caret>meta.xdef"
                                />
                                """,  //
                        "/nop/schema/xmeta.xdef" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "x:schema=\"/nop/schema/xdef.xdef\"", //
                                           "x:schema=\"/nop/sche<caret>ma/xdef.xdef\""), //
                        "/nop/schema/xdef.xdef" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef",//
                                           "xdsl:schema=\"/nop/schema/xdef.xdef\"", //
                                           "xdsl:schema=\"/nop/sche<caret>ma/xdef.xdef\""), //
                        "/nop/schema/xdef.xdef" //
        );
        // - xdef:default-extends=v-path
        assertReference("""
                                <form xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                      x:schema="/nop/schema/xdef.xdef"
                                      xdef:default-extends="/test/reference/de<caret>fault.xform"
                                />
                                """,  //
                        "/test/reference/default.xform" //
        );
        // - xpl:lib=v-path
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                >
                                    <x:gen-extends>
                                        <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/core/<caret>xlib/meta-gen.xlib"/>
                                    </x:gen-extends>
                                </meta>
                                """,  //
                        "/nop/core/xlib/meta-gen.xlib" //
        );
        // 对 v-path-list 列表元素的引用
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                                      x:extends="/test/reference/a<caret>.xmeta,/test/reference/b.xmeta"
                                />
                                """,  //
                        "/test/reference/a.xmeta" //
        );
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef"
                                      x:extends="/test/reference/a.xmeta,/test/reference/b.x<caret>meta"
                                />
                                """,  //
                        "/test/reference/b.xmeta" //
        );

        // 对 xdef-ref 类型属性的引用
        // - xmlns:xxx 默认为 xdef-ref 类型
        assertReference("""
                                <form xmlns:x="/nop/schema/xd<caret>sl.xdef"/>
                                """,  //
                        "/nop/schema/xdsl.xdef" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "xmlns:xdef=\"/nop/schema/xdef.xdef\"", //
                                           "xmlns:xdef=\"/nop/sche<caret>ma/xdef.xdef\""), //
                        "/nop/schema/xdef.xdef" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "xmlns:x=\"x\"", //
                                           "xmlns:x=\"x<caret>\""), //
                        null //
        );
        // - 在 *.xdef 中引用内部名字
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:ref=\"XDefNode\"", //
                                           "meta:ref=\"XDe<caret>fNode\""), //
                        "meta:define#meta:name=XDefNode" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
                                           "xdef:ref=\"DslNode\"", //
                                           "xdef:ref=\"Dsl<caret>Node\""), //
                        "xdef:unknown-tag#xdef:name=DslNode" //
        );
//        // - 引用文件的相对路径出现在开头：单元测试中暂时无法查找 vfs 相对路径
//        assertReference(insertCaretIntoVfs("/nop/schema/xui/simple-component.xdef", //
//                                           "xdef:ref=\"../xui/import.xdef\"", //
//                                           "xdef:ref=\"../xui/<caret>import.xdef\""), //
//                        "/nop/schema/xui/import.xdef" //
//        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <xdef:define xdef:name="PropNode"/>
                                     <prop name="string" xdef:ref="PropN<caret>ode"/>
                                </example>
                                """,  //
                        "xdef:define#xdef:name=PropNode" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <xdef:define xdef:name="PropNode"/>
                                     <prop name="string" xdef:ref="Prop<caret>Node1"/>
                                </example>
                                """, //
                        null //
        );
        // - 在 *.xdef 中引用外部文件
        assertReference("""
                                <meta xmlns:x="/nop/sch<caret>ema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                />
                                """,  //
                        "/nop/schema/xdsl.xdef" //
        );
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                      x:schema="/nop/schema/xdef.xdef"
                                      xdef:ref="/nop/schema/sch<caret>ema/obj-schema.xdef"
                                />
                                """,  //
                        "/nop/schema/schema/obj-schema.xdef" //
        );
        // - 在 *.xmeta 中引用外部文件中的节点
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                      ref="/test/reference/test-filter.xdef<caret>#FilterCondition"
                                />
                                """,  //
                        "/test/reference/test-filter.xdef?xdef:define#xdef:name=FilterCondition" //
        );
        // - 外部文件中的引用节点不存在
        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xmeta.xdef"
                                      ref="/test/reference/test-filter.xdef<caret>#FilterCondition1"
                                />
                                """, //
                        null //
        );
        // - 自引用
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <item xdef:name="Item0" xdef:ref="It<caret>em0"/>
                                     <xdef:define xdef:name="Item1" />
                                 </example>
                                """, //
                        null //
        );

        // 对 x:prototype 属性值的引用
        assertReference(insertCaretIntoVfs("/test/reference/user.view.xml", //
                                           "x:prototype=\"list\"", //
                                           "x:prototype=\"li<caret>st\""), //
                        "grid#id=list" //
        );
        assertReference(insertCaretIntoVfs("/test/reference/a.xlib", //
                                           "x:prototype=\"Get\"", //
                                           "x:prototype=\"G<caret>et\""), //
                        "Get" //
        );
        // - 引用不存在
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                    <grids>
                                        <grid id="pick-list" x:prototype="li<caret>st1"/>
                                    </grids>
                                </view>
                                """, //
                        null //
        );
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <NewGet x:prototype="G<caret>ot"/>
                                    </tags>
                                </lib>
                                """, //
                        null //
        );
        // - 自引用
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                    <grids>
                                        <grid id="pick-list" x:prototype="pick<caret>-list"/>
                                    </grids>
                                </view>
                                """, //
                        null //
        );
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <NewGet x:prototype="New<caret>Get"/>
                                    </tags>
                                </lib>
                                """, //
                        null //
        );

        // 对唯一键的引用
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:unique-attr=\"name\"", //
                                           "meta:unique-attr=\"n<caret>ame\""), //
                        "xdef:prop#name=!xml-name" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
                                           "meta:unique-attr=\"xdef:name\"", //
                                           "meta:unique-attr=\"xdef:<caret>name\""), //
                        "xdef:define#xdef:name=!var-name" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/conf.xdef", //
                                           "xdef:key-attr=\"name\"", //
                                           "xdef:key-attr=\"n<caret>ame\""), //
                        "var#name=!conf-name" //
        );
        // - 引用不存在
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop name="string" xdef:unique-attr="i<caret>d"/>
                                </example>
                                """, //
                        null //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <props xdef:body-type="list" xdef:key-attr="i<caret>d">
                                         <prop name="string"/>
                                     </props>
                                </example>
                                """, //
                        null //
        );

        assertReference("""
                                <meta xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/xdef.xdef"
                                >
                                    <xdef:pre-parse>
                                        <meta-gen:DefaultMetaGenExtends xpl:lib="/nop/core/<caret>xlib/meta-gen.xlib"/>
                                    </xdef:pre-parse>
                                </meta>
                                """,  //
                        "/nop/core/xlib/meta-gen.xlib" //
        );
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
                                """,  //
                        "/nop/core/xlib/meta-gen.xlib" //
        );

        // 非有效路径或未定义属性引用
        assertReference(insertCaretIntoVfs("/test/reference/user.view.xml", //
                                           "xmlns:view-gen=\"view-gen\"", //
                                           "xmlns:view-gen=\"vie<caret>w-gen\""), //
                        null //
        );
        assertReference("""
                                <dialog page="/test/reference/de<caret>fault.xform" />
                                """, //
                        null //
        );

        // generic-type、class-name、package-name 类型的值引用
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="st<caret>ring"/>
                                </example>
                                """,  //
                        "java.lang.String" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-class="io.nop.xui.initialize.VueNode<caret>StdDomainHandler"/>
                                </example>
                                """,  //
                        "io.nop.xui.initialize.VueNodeStdDomainHandler" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                        xdef:bean-package="io.nop.xlang.xd<caret>ef"
                                />
                                """,  //
                        "io.nop.xlang.xdef" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                        xdef:bean-package="io.nop.xlang.xdef.domain" xdef:name="XJso<caret>nDomainHandler"
                                />
                                """,  //
                        "io.nop.xlang.xdef.domain.XJsonDomainHandler" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                        xdef:name="XJso<caret>nDomainHandler"
                                />
                                """, //
                        null //
        );

        // dict/enum 类型的值引用
        assertReference(insertCaretIntoVfs("/nop/schema/xlib.xdef", //
                                           "<tags xdef:body-type=\"map\"", //
                                           "<tags xdef:body-type=\"m<caret>ap\""), //
                        "io.nop.xlang.xdef.XDefBodyType#map" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xlib.xdef", //
                                           "macro=\"!boolean=false\"", //
                                           "macro=\"!boo<caret>lean=false\""), //
                        "/dict/core/std-domain.dict.yaml#boolean" //
        );
        assertReference(insertCaretIntoVfs("/nop/schema/xlib.xdef", //
                                           "macro=\"!boolean=false\"", //
                                           "macro=\"!boolean=fal<caret>se\""), //
                        null //
        );

        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <child type="l<caret>eaf"/>
                                </example>
                                """,  //
                        "/dict/test/doc/child-type.dict.yaml#leaf" //
        );

        // 属性自引用
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop name="string" value="string=@attr:va<caret>lue"/>
                                </example>
                                """, //
                        null //
        );

        // x:schema 指定的 *.xdef 不存在，使得 DSL 的元模型未定义，导致模型属性未知，其引用将无法识别
        // - *.xdef 不存在
        assertReference("""
                                <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/sche<caret>ma/xform.xdef"/>
                                """, //
                        null //
        );
//        // - 属性未定义，引用无法识别：TODO 后续完成对 c:if 等内置函数的识别后，取消对普通 vfs 的文本识别
//        assertReference("""
//                                <form xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xform.xdef">
//                                    <filter def="/test/refere<caret>nce/test-filter.xdef"/>
//                                </form>
//                                """, //
//                        null //
//        );

        // 含 ${} 表达式的值，将被忽略
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                    <child type="${ty<caret>pe}"/>
                                </example>
                                """, //
                        null //
        );

        // 对 xpl 内置函数标签的属性值的识别
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:import from="/test/re<caret>ference/a.xlib"/>
                                      </x:gen-extends>
                                </view>
                                """,  //
                        "/test/reference/a.xlib" //
        );
        assertReference("""
                                <view xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xui/xview.xdef">
                                      <x:gen-extends>
                                          <c:include src="/test/re<caret>ference/a.xlib"/>
                                      </x:gen-extends>
                                </view>
                                """,  //
                        "/test/reference/a.xlib" //
        );

        // 对泛型、函数参数、数组的识别
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="Li<caret>st&lt;io.nop.xui.initialize.VueNodeStdDomainHandler>"/>
                                </example>
                                """,  //
                        "java.util.List" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="List&lt; io.nop.xui.initialize.VueNode<caret>StdDomainHandler>"/>
                                </example>
                                """,  //
                        "io.nop.xui.initialize.VueNodeStdDomainHandler" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="Ma<caret>p&lt; String,io.nop.xui.initialize.VueNodeStdDomainHandler>"/>
                                </example>
                                """,  //
                        "java.util.Map" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="Map&lt;Str<caret>ing ,io.nop.xui.initialize.VueNodeStdDomainHandler>"/>
                                </example>
                                """,  //
                        "java.lang.String" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="Map&lt; String, io.nop.xui.initialize.VueNode<caret>StdDomainHandler>"/>
                                </example>
                                """,  //
                        "io.nop.xui.initialize.VueNodeStdDomainHandler" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="(ar<caret>g1:int,arg2:Map<String,Integer>) => boolean"/>
                                </example>
                                """,  //
                        null //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="(arg1:i<caret>nt,arg2:Map<String,Integer>) => boolean"/>
                                </example>
                                """,  //
                        "java.lang.Integer" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="(arg1:int,arg2:Ma<caret>p<String,Integer>) => boolean"/>
                                </example>
                                """,  //
                        "java.util.Map" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="(arg1:int,arg2:Map< String, Int<caret>eger>) => boolean"/>
                                </example>
                                """,  //
                        "java.lang.Integer" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="(arg1:int,arg2:Map<String,Integer>) => boo<caret>lean"/>
                                </example>
                                """,  //
                        "java.lang.Boolean" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="in<caret>t[]"/>
                                </example>
                                """,  //
                        "java.lang.Integer" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="Ma<caret>p []"/>
                                </example>
                                """,  //
                        "java.util.Map" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="io.nop.xui.initialize.VueNode<caret>StdDomainHandler[]"/>
                                </example>
                                """,  //
                        "io.nop.xui.initialize.VueNodeStdDomainHandler" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="Li<caret>st&lt;io.nop.xui.initialize.VueNodeStdDomainHandler>[]"/>
                                </example>
                                """,  //
                        "java.util.List" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                        x:schema="/nop/schema/xdef.xdef"
                                >
                                     <prop xdef:bean-body-type="List&lt;io.nop.xui.initialize.VueNode<caret>StdDomainHandler>[]"/>
                                </example>
                                """,  //
                        "io.nop.xui.initialize.VueNodeStdDomainHandler" //
        );
    }

    public void testAttributeValueDefTypeReferences() {
//        assertReference(insertCaretIntoVfs("/nop/schema/xdef.xdef", //
//                                           "xdef:default-override=\"enum:io.nop.xlang.xdef.XDefOverride\"", //
//                                           "xdef:default-override=\"enum:io.nop.xlang.xdef.XDe<caret>fOverride\""), //
//                        "io.nop.xlang.xdef.XDefOverride" //
//        );
//
//        // 引用字典中定义的数据域
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <node xdef:value="v-<caret>path"/>
//                                </example>
//                                """,  //
//                        "/dict/core/std-domain.dict.yaml#v-path" //
//        );
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <node type="str<caret>ing"/>
//                                </example>
//                                """,  //
//                        "/dict/core/std-domain.dict.yaml#string" //
//        );
//
//        // 字典/枚举的 options 引用
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <child type="dict:test/doc/ch<caret>ild-type"/>
//                                </example>
//                                """,  //
//                        "/dict/test/doc/child-type.dict.yaml" //
//        );
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <child type="enum:lea<caret>f,node=leaf"/>
//                                </example>
//                                """, //
//                        null //
//        );
//        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
//                                           "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"", //
//                                           "x:override=\"enum:io.nop.xlang.xdef.X<caret>DefOverride=merge\""), //
//                        "io.nop.xlang.xdef.XDefOverride" //
//        );
//
//        // 字典/枚举的默认值引用
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <child type="dict:test/doc/child-type=le<caret>af"/>
//                                </example>
//                                """,  //
//                        "/dict/test/doc/child-type.dict.yaml#leaf" //
//        );
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <child type="enum:io.nop.xlang.xdef.XDefOverr<caret>Ide"/>
//                                </example>
//                                """,  //
//                        null //
//        );
//        assertReference("""
//                                <example xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <child type="enum:leaf,node=le<caret>af"/>
//                                </example>
//                                """, //
//                        null //
//        );
//        assertReference(insertCaretIntoVfs("/nop/schema/xdsl.xdef", //
//                                           "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=merge\"", //
//                                           "x:override=\"enum:io.nop.xlang.xdef.XDefOverride=me<caret>rge\""), //
//                        "io.nop.xlang.xdef.XDefOverride#MERGE" //
//        );
//
//        // 缺省属性值中 @attr: 引用
//        assertReference("""
//                                <component xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <import as="!var-name=@attr:n<caret>ame" name="var-name" from="!string"/>
//                                </component>
//                                """,  //
//                        "import#name=var-name" //
//        );
//        assertReference("""
//                                <component xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <var com="!var-name=@attr:name,ty<caret>pe" name="var-name" type="!string"/>
//                                </component>
//                                """,  //
//                        "var#type=!string" //
//        );
//        assertReference("""
//                                <component xmlns:x="/nop/schema/xdsl.xdef"
//                                         x:schema="/nop/schema/xdef.xdef"
//                                >
//                                    <var com="!var-name=@attr:ab<caret>c"/>
//                                </component>
//                                """, //
//                        null //
//        );

        // xlib 参数类型定义的引用
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <DoSomething>
                                            <attr name="disabled" type="Bo<caret>olean" stdDomain="boolean"/>
                                        </DoSomething>
                                    </tags>
                                </lib>
                                """, //
                        "java.lang.Boolean" //
        );
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <DoSomething>
                                            <attr name="disabled" type="Boolean" stdDomain="boo<caret>lean"/>
                                        </DoSomething>
                                    </tags>
                                </lib>
                                """, //
                        "/dict/core/std-domain.dict.yaml#boolean" //
        );
        assertReference("""
                                <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                    <tags>
                                        <Get>
                                            <attr name="path" stdDomain="v-path"/>
                                        </Get>
                                        <DoSomething>
                                            <source>
                                                <thisLib:Get path="/nop/sch<caret>ema/xlib.xdef"/>
                                            </source>
                                        </DoSomething>
                                    </tags>
                                </lib>
                                """, //
                        "/nop/schema/xlib.xdef" //
        );
    }

    public void testTextReferences() {
        assertReference(insertCaretIntoVfs("/test/reference/user.view.xml", //
                                           "<objMeta>", //
                                           "<objMeta><caret>"), //
                        "/test/reference/a.xmeta" //
        );

        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs>/test/reference/test<caret>-filter.xdef,/nop/schema/xdsl.xdef</refs>
                                </example>
                                """,  //
                        "/test/reference/test-filter.xdef" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs>
                                         /test/reference/test-filter.xdef,/nop/schema/x<caret>dsl.xdef
                                    </refs>
                                </example>
                                """,  //
                        "/nop/schema/xdsl.xdef" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs><![CDATA[
                                         /test/reference/tes<caret>t-filter.xdef, /nop/schema/xdsl.xdef
                                    ]]></refs>
                                </example>
                                """,  //
                        "/test/reference/test-filter.xdef" //
        );
        assertReference("""
                                <example xmlns:x="/nop/schema/xdsl.xdef"
                                         x:schema="/test/doc/example.xdef"
                                >
                                    <refs><![CDATA[
                                         /test/reference/test-filter.xdef,
                                           /nop/schema<caret>/xdsl.xdef
                                    ]]></refs>
                                </example>
                                """,  //
                        "/nop/schema/xdsl.xdef" //
        );
    }

    public void testVfsPathReferencesInJava() {
        assertReference("""
                                package io.nop.xlang.xdef;
                                public interface XDefConstants {
                                  String XDEF_XDSL_PATH = "/nop/sche<caret>ma/xdsl.xdef";
                                }
                                """, //
                        "java",  //
                        "/nop/schema/xdsl.xdef" //
        );
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void assertReference(String text, String expected) {
        assertReference(text, null, expected);
    }

    /** 通过在 <code>text</code> 中插入 <code>&lt;caret&gt;</code> 代表光标位置 */
    private void assertReference(String text, String suffix, String expected) {
        configureByText(text, suffix);

        PsiReference ref = findReferenceAtCaret();
        PsiElement target = ref != null ? ref.resolve() : null;

        if (expected == null) {
            assertNull(target);
            return;
        }
        assertNotNull(target);

        assertEquals(expected, toString(target));
    }

    private String toString(@NotNull PsiElement target) {
        // Note: 可能不是 vfs 文件中的元素
        String vfsPath = XmlPsiHelper.getNopVfsPath(target);

        if (target instanceof XmlTag tag) {
            return (vfsPath != null ? vfsPath + '?' : "") + tag.getName();
        } //
        else if (target instanceof SchemaPrefix ns) {
            return ns.getName();
        } //
        else if (target instanceof NopVirtualFile vfs) {
            PsiElement child = vfs.getFirstChild();

            return child instanceof PsiFile ? vfs.getPath() : toString(child);
        } //
        else if (target instanceof XmlAttribute attr) {
            XmlTag tag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            assert tag != null;

            return toString(tag) + '#' + attr.getName() + '=' + attr.getValue();
        } //
        else if (target instanceof PsiClass cls) {
            return cls.getQualifiedName();
        } //
        else if (target instanceof PsiPackage pkg) {
            return pkg.getQualifiedName();
        } //
        else if (target instanceof PsiField field) {
            PsiClass clazz = field.getContainingClass();
            assert clazz != null;

            return clazz.getQualifiedName() + "#" + field.getName();
        } //
        else if (target instanceof PsiPlainText txt) {
            return vfsPath + ":" + txt.getTextOffset();
        } //
        else if (target instanceof LeafPsiElement leaf) {
            return vfsPath + "#" + leaf.getText();
        } //
        else {
            fail("Unknown target " + target.getClass());
        }
        return null;
    }
}
