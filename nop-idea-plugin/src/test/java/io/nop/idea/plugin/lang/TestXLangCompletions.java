/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import java.util.Arrays;

import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.reference.XLangReferenceHelper;

public class TestXLangCompletions extends BaseXLangPluginTestCase {

    public void testNameSort() {
        String[] names = new String[] {
                "x:name", //
                "x:id", //
                "xdef:ref", //
                "xui:name", //
                "xdef:name", //
                "name", //
                "xui:label", //
                "value", //
        };

        Arrays.sort(names, XLangReferenceHelper.XLANG_NAME_COMPARATOR);

        assertEquals(String.join(", ", new String[] {
                "name", //
                "value", //
                "xdef:name", //
                "xdef:ref", //
                "x:id", //
                "x:name", //
                "xui:label", //
                "xui:name", //
        }), String.join(", ", names));
    }

    public void testTagCompletion() {
        // 从名字空间开始补全
        assertCompletion("xdef:unknown-tag", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <xd<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <xdef:unknown-tag></xdef:unknown-tag>
                                 </example>
                                 """);
        assertCompletion("x:gen-extends", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends></x:gen-extends>
                                 </example>
                                 """);

        // 从名字空间之后补全
        assertCompletion("unknown-tag", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <xdef:<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <xdef:unknown-tag></xdef:unknown-tag>
                                 </example>
                                 """);
        assertCompletion("post-parse", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <xdef:p<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <xdef:post-parse></xdef:post-parse>
                                 </example>
                                 """);

        assertCompletion("gen-extends", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends></x:gen-extends>
                                 </example>
                                 """);

        // 不带名字空间的补全
        assertCompletion("""
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <tag-no-<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <tag-no-child></tag-no-child>
                                 </example>
                                 """);
    }

    public void testAttributeCompletion() {
        // 从名字空间开始补全
        assertCompletion("xdsl:dump", //
                         """
                                 <xdef:unknown-tag
                                     xmlns:xdsl="/nop/schema/xdsl.xdef"
                                     xdsl:schema="/nop/schema/xdef.xdef"
                                     xd<caret>
                                 >
                                 </xdef:unknown-tag>
                                 """, //
                         """
                                 <xdef:unknown-tag
                                     xmlns:xdsl="/nop/schema/xdsl.xdef"
                                     xdsl:schema="/nop/schema/xdef.xdef"
                                     xdsl:dump=""
                                 >
                                 </xdef:unknown-tag>
                                 """);
        assertCompletion("meta:bean-package", //
                         """
                                 <meta:unknown-tag
                                     xmlns:x="/nop/schema/xdsl.xdef"
                                     xmlns:meta="/nop/schema/xdef.xdef"
                                     x:schema="/nop/schema/xdef.xdef"
                                     met<caret>
                                 >
                                 </meta:unknown-tag>
                                 """, //
                         """
                                 <meta:unknown-tag
                                     xmlns:x="/nop/schema/xdsl.xdef"
                                     xmlns:meta="/nop/schema/xdef.xdef"
                                     x:schema="/nop/schema/xdef.xdef"
                                     meta:bean-package=""
                                 >
                                 </meta:unknown-tag>
                                 """);

        assertCompletion("xdef:default-extends", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                          xd<caret>
                                 >
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                          xdef:default-extends=""
                                 >
                                 </example>
                                 """);
        assertCompletion("x:extends", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                          x<caret>
                                 >
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                          x:extends=""
                                 >
                                 </example>
                                 """);

        assertCompletion("xdef:value", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <child xd<caret> />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <child xdef:value="" />
                                 </example>
                                 """);
        assertCompletion("x:override", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <child x<caret> />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <child x:override="" />
                                 </example>
                                 """);

        assertCompletion("xpl:enableNs", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends>
                                        <abc xp<caret>
                                     </x:gen-extends>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends>
                                        <abc xpl:enableNs=""
                                     </x:gen-extends>
                                 </example>
                                 """);

        // 从名字空间之后补全
        assertCompletion("dump", //
                         """
                                 <xdef:unknown-tag
                                     xmlns:xdsl="/nop/schema/xdsl.xdef"
                                     xdsl:schema="/nop/schema/xdef.xdef"
                                     xdsl:<caret>
                                 >
                                 </xdef:unknown-tag>
                                 """, //
                         """
                                 <xdef:unknown-tag
                                     xmlns:xdsl="/nop/schema/xdsl.xdef"
                                     xdsl:schema="/nop/schema/xdef.xdef"
                                     xdsl:dump=""
                                 >
                                 </xdef:unknown-tag>
                                 """);
        assertCompletion("bean-package", //
                         """
                                 <meta:unknown-tag
                                     xmlns:x="/nop/schema/xdsl.xdef"
                                     xmlns:meta="/nop/schema/xdef.xdef"
                                     x:schema="/nop/schema/xdef.xdef"
                                     meta:<caret>
                                 >
                                 </meta:unknown-tag>
                                 """, //
                         """
                                 <meta:unknown-tag
                                     xmlns:x="/nop/schema/xdsl.xdef"
                                     xmlns:meta="/nop/schema/xdef.xdef"
                                     x:schema="/nop/schema/xdef.xdef"
                                     meta:bean-package=""
                                 >
                                 </meta:unknown-tag>
                                 """);

        assertCompletion("value", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <child xdef:<caret> />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <child xdef:value="" />
                                 </example>
                                 """);
        assertCompletion("override", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xdef.xdef">
                                     <child x:<caret> />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xdef.xdef">
                                     <child x:override="" />
                                 </example>
                                 """);

        assertCompletion("enableNs", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends>
                                        <abc xpl:<caret>
                                     </x:gen-extends>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends>
                                        <abc xpl:enableNs=""
                                     </x:gen-extends>
                                 </example>
                                 """);

        assertCompletion("""
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <refs xdef:val<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <refs xdef:value=""
                                 </example>
                                 """);
        assertCompletion("unknown-attr", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <xdef:unknown-tag xdef:unknown-a<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef"
                                 >
                                     <xdef:unknown-tag xdef:unknown-attr=""
                                 </example>
                                 """);

        // 不带名字空间的补全
        assertCompletion("""
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <var com="abc" x:key-at<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <var com="abc" x:key-attr=""
                                 </example>
                                 """);

        assertCompletion("name", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <var com="abc" na<caret>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <var com="abc" name=""
                                 </example>
                                 """);

        // XPL 属性补全
        assertCompletion("""
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends>
                                        <abc xpl:enable<caret>
                                     </x:gen-extends>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <x:gen-extends>
                                        <abc xpl:enableNs=""
                                     </x:gen-extends>
                                 </example>
                                 """);
    }

    public void testAttributeValueCompletion() {
        // 对 x:prototype 属性值的补全
        // - 引用兄弟节点标签名
        assertCompletion("Get2", //
                         """
                                 <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                     <tags>
                                         <ExtendGet x:prototype="Get<caret>"/>
                                         <Get1></Get1>
                                         <Get2></Get2>
                                     </tags>
                                 </lib>
                                 """, //
                         """
                                 <lib xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xlib.xdef">
                                     <tags>
                                         <ExtendGet x:prototype="Get2"/>
                                         <Get1></Get1>
                                         <Get2></Get2>
                                     </tags>
                                 </lib>
                                 """);
        // - 引用 xdef:body-type="list" 类型父节点通过 xdef:key-attr 指定的兄弟节点的唯一键属性的值
        assertCompletion("prop1", //
                         """
                                 <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef">
                                    <props>
                                        <prop x:abstract="true" name="prop1"/>
                                        <prop name="prop2"/>
                                        <prop name="extend-prop" x:prototype="pro<caret>"/>
                                    </props>
                                 </meta>
                                 """, //
                         """
                                 <meta xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/xmeta.xdef">
                                    <props>
                                        <prop x:abstract="true" name="prop1"/>
                                        <prop name="prop2"/>
                                        <prop name="extend-prop" x:prototype="prop1"/>
                                    </props>
                                 </meta>
                                 """);
        // - 引用兄弟节点通过 xdef:unique-attr 指定的唯一键属性的值
        assertCompletion("res2", //
                         """
                                 <site xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/site.xdef">
                                    <resource id="res1"/>
                                    <resource id="res2"/>
                                    <resource id="extend-res" x:prototype="res<caret>"/>
                                 </site>
                                 """, //
                         """
                                 <site xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/site.xdef">
                                    <resource id="res1"/>
                                    <resource id="res2"/>
                                    <resource id="extend-res" x:prototype="res2"/>
                                 </site>
                                 """);
        assertCompletion("xdef:name", //
                         """
                                 <meta:unknown-tag xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                     x:schema="/nop/schema/xdef.xdef"
                                 >
                                    <xdef:define xdef:name="!var-name" name="string"
                                                 meta:unique-attr="na<caret>"
                                    />
                                 </meta:unknown-tag>
                                 """, //
                         """
                                 <meta:unknown-tag xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                     x:schema="/nop/schema/xdef.xdef"
                                 >
                                    <xdef:define xdef:name="!var-name" name="string"
                                                 meta:unique-attr="xdef:name"
                                    />
                                 </meta:unknown-tag>
                                 """);

        // 对 xdef:key-attr 属性值的补全
        assertCompletion("name", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <props xdef:body-type="list" xdef:key-attr="e<caret>">
                                        <prop1 id="!string" name="!var-name" value="xml-name"/>
                                        <prop2 use="!string" name="!var-name" value="xml-name"/>
                                     </props>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <props xdef:body-type="list" xdef:key-attr="name">
                                        <prop1 id="!string" name="!var-name" value="xml-name"/>
                                        <prop2 use="!string" name="!var-name" value="xml-name"/>
                                     </props>
                                 </example>
                                 """);

        // 对 xdef:unique-attr/xdef:order-attr 属性值的补全
        assertCompletion("name", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                    <prop xdef:unique-attr="e<caret>"
                                          id="!string" name="!var-name" value="xml-name"
                                    />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                    <prop xdef:unique-attr="name"
                                          id="!string" name="!var-name" value="xml-name"
                                    />
                                 </example>
                                 """);
        assertCompletion("value", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                    <prop xdef:order-attr="e<caret>"
                                          id="!string" name="!var-name" value="xml-name"
                                    />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                    <prop xdef:order-attr="value"
                                          id="!string" name="!var-name" value="xml-name"
                                    />
                                 </example>
                                 """);
    }

    public void testAttributeValueCompletionForDefType() {
        // 对 xdef-ref 引用目标的补全
        // - 引用当前 *.xdef 中的节点
        assertCompletion("Item2", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <item xdef:ref="It<caret>"/>
                                     <xdef:define xdef:name="Item1" />
                                     <xdef:define xdef:name="Item2" />
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <item xdef:ref="Item2"/>
                                     <xdef:define xdef:name="Item1" />
                                     <xdef:define xdef:name="Item2" />
                                 </example>
                                 """);
        // - 引用外部 *.xdef
        assertCompletion("/nop/schema/xui/xview.xdef", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <schema xdef:ref="/nop/schema<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <schema xdef:ref="/nop/schema/xui/xview.xdef"/>
                                 </example>
                                 """);
//        // - 引用外部 *.xdef 中的节点：TODO 对外部 xdef 中节点的引用，没有实际需求，暂不支持
//        assertCompletion("SiteResourceBean", //
//                         """
//                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
//                                          x:schema="/nop/schema/xdef.xdef">
//                                     <resource xdef:ref="/nop/schema/site.xdef#Bean<caret>"/>
//                                 </example>
//                                 """, //
//                         """
//                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
//                                          x:schema="/nop/schema/xdef.xdef">
//                                     <resource xdef:ref="/nop/schema/site.xdef#SiteResourceBean"/>
//                                 </example>
//                                 """);

        // 对字典项/枚举项的补全
        assertCompletion("append", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource xdef:default-override="<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource xdef:default-override="append"/>
                                 </example>
                                 """);
        assertCompletion("leaf", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <child type="<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <child type="leaf"/>
                                 </example>
                                 """);

//        // 对 vfs 的补全：TODO 暂不支持对文本节点的补全
//        assertCompletion("/nop/schema/xdsl.xdef", //
//                         """
//                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
//                                     <refs>/nop/schema/<caret></refs>
//                                 </example>
//                                 """, //
//                         """
//                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
//                                     <refs>/nop/schema/xdsl.xdef</refs>
//                                 </example>
//                                 """);
//        assertCompletion("/test/doc/example.xdef", //
//                         """
//                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
//                                     <refs>/nop/schema/xdsl.xdef,/test/doc<caret></refs>
//                                 </example>
//                                 """, //
//                         """
//                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
//                                     <refs>/nop/schema/xdsl.xdef,/test/doc/example.xdef</refs>
//                                 </example>
//                                 """);

        // 对 boolean 的补全
        assertCompletion("false", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <child x:abstract="<caret>">
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" x:schema="/test/doc/example.xdef">
                                     <child x:abstract="false">
                                 </example>
                                 """);

        // 对数据域的补全
        // - 补全修饰符
        assertCompletion("!", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource name="<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource name="!"/>
                                 </example>
                                 """);
        // - 补全数据域名字
        assertCompletion("var-name", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource name="!<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource name="!var-name"/>
                                 </example>
                                 """);
        // - 补全 enum/dict 的选项
        assertCompletion("io.nop.xlang.xdef.XDefOverride", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!enum:io.nop.xlang.xdef.<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!enum:io.nop.xlang.xdef.XDefOverride"/>
                                 </example>
                                 """);
        assertCompletion("test/doc/child-type", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!dict:<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!dict:test/doc/child-type"/>
                                 </example>
                                 """);
        // - 补全 enum/dict 的缺省值
        assertCompletion("merge", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!enum:io.nop.xlang.xdef.XDefOverride=<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!enum:io.nop.xlang.xdef.XDefOverride=merge"/>
                                 </example>
                                 """);
        assertCompletion("node", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!dict:test/doc/child-type=<caret>"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!dict:test/doc/child-type=node"/>
                                 </example>
                                 """);
        // - 补全缺省值的属性引用
        assertCompletion("path", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!var-name=@attr:<caret>" path="!var-name" name="string"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!var-name=@attr:path" path="!var-name" name="string"/>
                                 </example>
                                 """);
        assertCompletion("name", //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!var-name=@attr:path,<caret>"
                                               path="!var-name" name="string" url="string"/>
                                 </example>
                                 """, //
                         """
                                 <example xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                          x:schema="/nop/schema/xdef.xdef">
                                     <resource type="!var-name=@attr:path,name"
                                               path="!var-name" name="string" url="string"/>
                                 </example>
                                 """);
    }

    /** 需确保仅有唯一一项自动填充项：匹配是模糊匹配，需增加输入长度才能做唯一匹配 */
    protected void assertCompletion(String text, String expectedText) {
        configureByXLangText(text);
        myFixture.completeBasic();

        myFixture.checkResult(expectedText);
    }

    protected void assertCompletion(String selectedItem, String text, String expectedText) {
        configureByXLangText(text);
        myFixture.completeBasic();

        doAssertCompletion(selectedItem, expectedText);
    }
}
