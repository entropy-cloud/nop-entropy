/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang;

import java.util.function.BiConsumer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.psi.XLangTagMeta;
import io.nop.idea.plugin.utils.XmlPsiHelper;

/**
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-10-22
 */
public class TestXLangTagMeta extends BaseXLangPluginTestCase {

    public void testCreateTagMeta() {
        // xdef.xdef
        assertTagMeta("""
                              <meta:un<caret>known-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("meta:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:un<caret>known-tag
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("meta:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <meta:de<caret>fine></meta:define>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("meta:define", tagMeta.getTagName());
                          assertEquals("xdef:define", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:pre<caret>-parse meta:value="xpl"/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("xdef:pre-parse", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <meta:pre<caret>-parse/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("meta:pre-parse", tagMeta.getTagName());
                          assertEquals("xdef:pre-parse", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <meta:de<caret>fine/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("meta:define", tagMeta.getTagName());
                          assertEquals("xdef:define", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <meta:define>
                                  <xdef:unknow<caret>n-tag meta:ref="XDefNode"/>
                                </meta:define>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("xdef:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <x:g<caret>en-extends/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        // - xpl node
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <meta:pre-parse>
                                  <c:scr<caret>ipt />
                                </meta:pre-parse>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <x:gen-extends>
                                  <c:scr<caret>ipt />
                                </x:gen-extends>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );

        // xdsl.xdef
        assertTagMeta("""
                              <xdef:unkno<caret>wn-tag
                                xmlns:xdef="/nop/schema/xdef.xdef" xmlns:xdsl="/nop/schema/xdsl.xdef"
                                xdsl:schema="/nop/schema/xdef.xdef"
                              >
                              </xdef:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("xdef:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <xdef:unknown-tag
                                xmlns:xdef="/nop/schema/xdef.xdef" xmlns:xdsl="/nop/schema/xdsl.xdef"
                                xdsl:schema="/nop/schema/xdef.xdef"
                              >
                                <x:gen-ex<caret>tends xdef:value="xpl-node"/>
                              </xdef:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <xdef:unknown-tag
                                xmlns:xdef="/nop/schema/xdef.xdef" xmlns:xdsl="/nop/schema/xdsl.xdef"
                                xdsl:schema="/nop/schema/xdef.xdef"
                              >
                                <xdsl:gen-ex<caret>tends/>
                              </xdef:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("xdsl:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <xdef:unknown-tag
                                xmlns:xdef="/nop/schema/xdef.xdef" xmlns:xdsl="/nop/schema/xdsl.xdef"
                                xdsl:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:unknown-tag>
                                  <x:su<caret>per xdef:internal="true"/>
                                </xdef:unknown-tag>
                              </xdef:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("x:super", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <xdef:unknown-tag
                                xmlns:xdef="/nop/schema/xdef.xdef" xmlns:xdsl="/nop/schema/xdsl.xdef"
                                xdsl:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:unknown-tag>
                                  <xdsl:su<caret>per/>
                                </xdef:unknown-tag>
                              </xdef:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("xdsl:super", tagMeta.getTagName());
                          assertEquals("x:super", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        // - xpl node
        assertTagMeta("""
                              <xdef:unknown-tag
                                xmlns:xdef="/nop/schema/xdef.xdef" xmlns:xdsl="/nop/schema/xdsl.xdef"
                                xdsl:schema="/nop/schema/xdef.xdef"
                              >
                                <xdsl:gen-extends>
                                  <c:scr<caret>ipt />
                                </xdsl:gen-extends>
                              </xdef:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );

        // Normal xdef
        assertTagMeta("""
                              <exa<caret>mple
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("example", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:po<caret>st-parse>
                                </xdef:post-parse>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("xdef:post-parse", tagMeta.getTagName());
                          assertEquals("xdef:post-parse", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <x:ge<caret>n-extends>
                                </x:gen-extends>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <re<caret>fs xdef:value="v-path-list"/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("refs", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:unk<caret>nown-tag xdef:unknown-attr="any"/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertTrue(tagMeta.isXdefDefNode());
                          assertEquals("xdef:unknown-tag", tagMeta.getTagName());
                          assertEquals("xdef:unknown-tag", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        // - xpl node
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <x:gen-extends>
                                  <c:scr<caret>ipt />
                                </x:gen-extends>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );

        // Normal DSL
        assertTagMeta("""
                              <exa<caret>mple
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("example", tagMeta.getTagName());
                          assertEquals("example", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/test/lang/lang.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <chi<caret>ld name="Child" type="leaf" abc="abc"/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("child", tagMeta.getTagName());
                          assertEquals("child", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/test/lang/lang.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <un<caret>known name="abc"/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertTrue(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("unknown", tag.getName());
                          assertNull(tagMeta.getTagName());
                          assertNull(tagMeta.getDefNodeInSchema());
                      } //
        );
        // - xdsl node
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <x:ge<caret>n-extends/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        // - xpl node
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <x:gen-extends>
                                  <c:scr<caret>ipt />
                                </x:gen-extends>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        // - undefined namespace node
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <yui:st<caret>yle/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("yui:style", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <xui:st<caret>yle/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertTrue(tagMeta.isUnknown());
                          assertFalse(tagMeta.isXdefDefNode());
                          assertEquals("xui:style", tag.getName());
                          assertNull(tagMeta.getTagName());
                          assertNull(tagMeta.getDefNodeInSchema());
                      } //
        );
    }

    private void assertTagMeta(String text, BiConsumer<XLangTag, XLangTagMeta> consumer) {
        configureByXLangText(text);
        assertCaretExists();

        PsiElement target = getOriginalElementAtCaret();
        XLangTag tag = PsiTreeUtil.getParentOfType(target, XLangTag.class);
        assertNotNull(tag);

        XLangTagMeta tagMeta = tag.getTagMeta();
        assertNotNull(tagMeta);

        consumer.accept(tag, tagMeta);
    }

    private String defNodeVfsPath(XLangTagMeta tagMeta) {
        String vfsPath = XmlPsiHelper.getNopVfsPath(tagMeta.getDefNodeInSchema());

        return vfsPath != null && vfsPath.lastIndexOf('/') == 0 ? null : vfsPath;
    }
}
