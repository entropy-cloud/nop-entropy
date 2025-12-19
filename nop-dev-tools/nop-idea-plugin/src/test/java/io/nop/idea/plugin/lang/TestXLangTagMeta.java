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
import io.nop.idea.plugin.lang.psi.XLangAttribute;
import io.nop.idea.plugin.lang.psi.XLangTag;
import io.nop.idea.plugin.lang.psi.XLangTagMeta;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;

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
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("meta:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertTrue(tagMeta.getDefNodeInSelfSchema().isUnknownTag());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("meta:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <meta:de<caret>fine></meta:define>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("meta:define", tagMeta.getTagName());
                          assertEquals("xdef:define", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <xdef:pre<caret>-parse meta:value="xpl"/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("xdef:pre-parse", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <meta:pre<caret>-parse/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("meta:pre-parse", tagMeta.getTagName());
                          assertEquals("xdef:pre-parse", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <meta:de<caret>fine/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("meta:define", tagMeta.getTagName());
                          assertEquals("xdef:define", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                                meta:ref="XDefNode"
                              >
                                <meta:define meta:name="XDefNode">
                                  <meta:unknown-tag meta:ref="XDefNode"/>
                                  <xdef:unknow<caret>n-tag meta:ref="XDefNode"/>
                                  <xdef:define xdef:name="!var-name" meta:ref="XDefNode" meta:unique-attr="xdef:name"/>
                                </meta:define>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("xdef:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <x:g<caret>en-extends/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );
        // - xpl node
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <meta:pre-parse>
                                  <c:scr<caret>ipt />
                                </meta:pre-parse>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <x:gen-extends>
                                  <c:scr<caret>ipt />
                                </x:gen-extends>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertTrue(tagMeta.isInXdefSchema());

                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xdef:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertTrue(tagMeta.getDefNodeInSelfSchema().isUnknownTag());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xdsl:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("x:super", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xdsl:super", tagMeta.getTagName());
                          assertEquals("x:super", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("example", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xdef:post-parse", tagMeta.getTagName());
                          assertEquals("xdef:post-parse", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("refs", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertEquals(tagMeta.getTagName(), tagMeta.getDefNodeInSelfSchema().getTagName());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertFalse(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xdef:unknown-tag", tagMeta.getTagName());
                          assertEquals("xdef:unknown-tag", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdef.xdef", defNodeVfsPath(tagMeta));

                          assertNotNull(tagMeta.getDefNodeInSelfSchema());
                          assertTrue(tagMeta.getDefNodeInSelfSchema().isUnknownTag());
                          assertNull(selfDefNodeVfsPath(tagMeta));
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
                          assertTrue(tagMeta.isXplNode());
                          assertTrue(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("example", tagMeta.getTagName());
                          assertEquals("example", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/test/lang/lang.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("child", tagMeta.getTagName());
                          assertEquals("child", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/test/lang/lang.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("unknown", tagMeta.getTagName());
                          assertNull(tagMeta.getDefNodeInSchema());

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertTrue(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("x:gen-extends", tagMeta.getTagName());
                          assertEquals("x:gen-extends", tagMeta.getDefNodeInSchema().getTagName());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertTrue(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("c:script", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xpl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("yui:style", tagMeta.getTagName());
                          assertTrue(tagMeta.getDefNodeInSchema().isUnknownTag());
                          assertEquals("/nop/schema/xdsl.xdef", defNodeVfsPath(tagMeta));

                          assertNull(tagMeta.getDefNodeInSelfSchema());
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
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xui:style", tagMeta.getTagName());
                          assertNull(tagMeta.getDefNodeInSchema());

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );

        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <xjson>
                                  <n<caret>ame>Tom</name>
                                  <age>23</age>
                                </xjson>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("name", tagMeta.getTagName());
                          assertNotNull(tagMeta.getDefNodeInSchema());
                          // 实际为 xdsl.xdef 的 xdef:unknown-tag 节点
                          assertEquals(tagMeta.getDefNodeInSchema(), tagMeta.getXDslDefNode());

                          assertNull(tagMeta.getDefNodeInSelfSchema());
                      } //
        );
    }

    public void testCreateUnknownTagMeta() {
        assertTagMeta("""
                              <meta:un<caret>known-tag>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          // 元模型未指定
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("meta:unknown-tag", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("No schema path is specified"));
                      } //
        );

        assertTagMeta("""
                              <meta:un<caret>known
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                              </meta:unknown>
                              """, //
                      (tag, tagMeta) -> {
                          // meta 名字空间的标签未定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("meta:unknown", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("corresponding namespace 'xdef'"));
                      } //
        );
        assertTagMeta("""
                              <meta:unknown-tag
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:meta="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef" meta:check-ns="xdef"
                              >
                                <meta:ab<caret>cd/>
                              </meta:unknown-tag>
                              """, //
                      (tag, tagMeta) -> {
                          // meta 名字空间的标签未定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("meta:abcd", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("corresponding namespace 'xdef'"));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:som<caret>ething>
                                </xdef:something>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          // xdef 名字空间的标签未定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xdef:something", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("isn't defined in schema '/nop/schema/xdef.xdef'"));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <x:a<caret>ny/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          // x 名字空间的标签未定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("x:any", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("corresponding namespace 'x'"));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <x:post-extends>
                                  <xpl:ab<caret>c/>
                                </x:post-extends>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          // xpl 名字空间的标签未定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xpl:abc", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("corresponding namespace 'xpl'"));
                      } //
        );

        assertTagMeta("""
                              <la<caret>ng
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                              </lang>
                              """, //
                      (tag, tagMeta) -> {
                          // 根节点标签与定义的不一致
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("lang", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("doesn't match with the root tag"));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <xui:par<caret>ent>
                                </xui:parent>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          // xui 名字空间的标签未显式定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xui:parent", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("should be defined in schema"));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <xui:parent>
                                  <xui:ch<caret>ild/>
                                </xui:parent>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          // 父标签未定义
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("xui:child", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("'xui:parent' isn't defined"));
                      } //
        );
        assertTagMeta("""
                              <exa<caret>mple
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/xxx/xxx/example.xdef"
                              />
                              """, //
                      (tag, tagMeta) -> {
                          // 元模型加载失败
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("example", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("parse-missing-resource"));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/xxx/xxx/example.xdef"
                              >
                                <chi<caret>ld/>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          // 元模型加载失败
                          assertTrue(tagMeta.hasError());
                          assertFalse(tagMeta.isXplNode());
                          assertFalse(tagMeta.isInAnySchema());
                          assertFalse(tagMeta.isInXdefSchema());

                          assertEquals("child", tagMeta.getTagName());
                          assertTrue(tagMeta.getErrorMsg().contains("'example' isn't defined"));
                      } //
        );
    }

    public void testDefAttrByTagMeta() {
        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <refs i18n:ti<caret>tle="Refs" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // 名字空间未要求校验，则其下属性可任意指定
                          assertEquals("i18n:title", attr.getName());

                          assertNotNull(defAttr);
                          assertEquals("any", defAttr.getType().getStdDomain());
                      } //
        );

        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <some tit<caret>le="Some" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // 无名字空间的属性必须显式定义
                          assertEquals("title", attr.getName());

                          assertNotNull(defAttr);
                          assertInstanceOf(defAttr, XLangAttribute.XDefAttributeWithError.class);
                          assertTrue(((XLangAttribute.XDefAttributeWithError) defAttr).getErrorMsg()
                                                                                      .contains(
                                                                                              "is defined on undefined tag"));
                      } //
        );
        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <some i18n:tit<caret>le="Some" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // 在未定义标签上的属性也为未定义
                          assertEquals("i18n:title", attr.getName());

                          assertNotNull(defAttr);
                          assertInstanceOf(defAttr, XLangAttribute.XDefAttributeWithError.class);
                          assertTrue(((XLangAttribute.XDefAttributeWithError) defAttr).getErrorMsg()
                                                                                      .contains(
                                                                                              "is defined on undefined tag"));
                      } //
        );

        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <refs xui:cla<caret>ss="w-full" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // 需校验的名字空间，其下属性必须显式定义
                          assertEquals("xui:class", attr.getName());

                          assertNotNull(defAttr);
                          assertInstanceOf(defAttr, XLangAttribute.XDefAttributeWithError.class);
                          assertTrue(((XLangAttribute.XDefAttributeWithError) defAttr).getErrorMsg()
                                                                                      .contains(
                                                                                              "namespace 'xui' should be defined in schema '/test/lang/lang.xdef'"));
                      } //
        );
        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <refs x:cla<caret>ss="w-full" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // x 名字空间下的属性必须为 xdsl.xdef 中已定义的
                          assertEquals("x:class", attr.getName());

                          assertNotNull(defAttr);
                          assertInstanceOf(defAttr, XLangAttribute.XDefAttributeWithError.class);
                          assertTrue(((XLangAttribute.XDefAttributeWithError) defAttr).getErrorMsg()
                                                                                      .contains(
                                                                                              "namespace 'x' should be defined in schema '/nop/schema/xdsl.xdef'"));
                      } //
        );
        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <refs xdef:unde<caret>fined="string" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // xdef 名字空间下的属性必须为 xdef.xdef 中已定义的
                          assertEquals("xdef:undefined", attr.getName());

                          assertNotNull(defAttr);
                          assertInstanceOf(defAttr, XLangAttribute.XDefAttributeWithError.class);
                          assertTrue(((XLangAttribute.XDefAttributeWithError) defAttr).getErrorMsg()
                                                                                      .contains(
                                                                                              "namespace 'xdef' should be defined in schema '/nop/schema/xdef.xdef'"));
                      } //
        );
        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <refs x:unde<caret>fined="string" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // x 名字空间下的属性必须为 xdsl.xdef 中已定义的
                          assertEquals("x:undefined", attr.getName());

                          assertNotNull(defAttr);
                          assertInstanceOf(defAttr, XLangAttribute.XDefAttributeWithError.class);
                          assertTrue(((XLangAttribute.XDefAttributeWithError) defAttr).getErrorMsg()
                                                                                      .contains(
                                                                                              "namespace 'x' should be defined in schema '/nop/schema/xdsl.xdef'"));
                      } //
        );
        assertDefAttr("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <refs xui:cl<caret>ass="string" />
                              </example>
                              """, //
                      (attr, defAttr) -> {
                          // 在元模型中的带名字空间的属性可任意定义
                          assertEquals("xui:class", attr.getName());

                          assertNotNull(defAttr);
                          assertNotNull(defAttr.getType());
                          assertEquals("xdef-attr", defAttr.getType().getStdDomain());
                      } //
        );
    }

    public void testAllowedChildTagByTagMeta() {
        // 检查父节点是否允许多个子节点
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <u<caret>1/>
                                  <u2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <u1/>
                                  <u<caret>2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.only_at_most_one,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <x:gen-extends/>
                                  <u<caret>2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <abc/>
                                  <u<caret>2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <a<caret>bc/>
                                  <u2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <xui:abc/>
                                  <u<caret>2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <xui:a<caret>bc/>
                                  <u2/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <u1/>
                                  <a<caret>bc/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <union>
                                  <u1/>
                                  <xui:a<caret>bc/>
                                </union>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );

        // 检查节点自身的可重复性
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <list>
                                  <l<caret>1/>
                                  <l2/>
                                  <l1/>
                                </list>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <list>
                                  <l1/>
                                  <l<caret>2/>
                                  <l1/>
                                </list>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <list>
                                  <l1/>
                                  <l2/>
                                  <l<caret>1/>
                                </list>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.can_not_be_multiple,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <list>
                                  <l1/>
                                  <l2/>
                                  <l1/>
                                  <l<caret>2/>
                                </list>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );

        // 检查节点可嵌套性
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:def<caret>ine xdef:name="A">
                                  <xdef:define xdef:name="B"/>
                                </xdef:define>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:define xdef:name="A">
                                  <xdef:de<caret>fine xdef:name="B"/>
                                </xdef:define>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.can_not_be_nested_by_same_name_tag,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/nop/schema/xdef.xdef"
                              >
                                <xdef:define xdef:name="A">
                                  <child>
                                    <xdef:de<caret>fine xdef:name="B"/>
                                  </child>
                                </xdef:define>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.can_not_be_nested_by_same_name_tag,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
                      } //
        );
        // - 在非 *.xdef 中，不检查 xdef:define 的嵌套性
        assertTagMeta("""
                              <example
                                xmlns:x="/nop/schema/xdsl.xdef"
                                x:schema="/test/lang/lang.xdef"
                              >
                                <xdef:define xdef:name="A">
                                  <child>
                                    <xdef:de<caret>fine xdef:name="B"/>
                                  </child>
                                </xdef:define>
                              </example>
                              """, //
                      (tag, tagMeta) -> {
                          assertNotNull(tag.getParentTag());

                          XLangTagMeta parentTagMeta = tag.getParentTag().getTagMeta();
                          assertEquals(XLangTagMeta.ChildTagAllowedMode.allowed,
                                       parentTagMeta.checkChildTagAllowed(tagMeta));
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

    private void assertDefAttr(String text, BiConsumer<XLangAttribute, IXDefAttribute> consumer) {
        configureByXLangText(text);
        assertCaretExists();

        PsiElement target = getOriginalElementAtCaret();
        XLangAttribute attr = PsiTreeUtil.getParentOfType(target, XLangAttribute.class);
        assertNotNull(attr);

        XLangTagMeta tagMeta = attr.getParentTag().getTagMeta();
        assertNotNull(tagMeta);

        consumer.accept(attr, tagMeta.getDefAttr(attr));
    }

    private String defNodeVfsPath(XLangTagMeta tagMeta) {
        return defNodeVfsPath(tagMeta.getDefNodeInSchema());
    }

    private String selfDefNodeVfsPath(XLangTagMeta tagMeta) {
        return defNodeVfsPath(tagMeta.getDefNodeInSelfSchema());
    }

    private String defNodeVfsPath(IXDefNode defNode) {
        String vfsPath = XmlPsiHelper.getNopVfsPath(defNode);

        return vfsPath != null && vfsPath.lastIndexOf('/') == 0 ? null : vfsPath;
    }
}
