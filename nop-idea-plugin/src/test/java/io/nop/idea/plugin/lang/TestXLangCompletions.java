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
import io.nop.idea.plugin.lang.reference.XLangTagReference;

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

        Arrays.sort(names, XLangTagReference.NAME_COMPARATOR);

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
