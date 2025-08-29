/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiFile;
import io.nop.idea.plugin.BaseXLangPluginTestCase;
import org.junit.Assert;

public class TestXLangFileTypeDetector extends BaseXLangPluginTestCase {

    public void testDetectXLangByExtension() {
        String[] extensions = "xdef;xpl;xgen;xui;xlib;xrun;xwf;xmeta;xpage;xrule".split(";");

        for (String ext : extensions) {
            assertFileType("a." + ext, "", XLangFileType.INSTANCE);
        }
    }

    public void testDetectXLangFromXml() {
        String[] samples = new String[] {
                "", //
                """
                               <workflow></workflow>
                               """, //
                """
                               <workflow/>
                               """, //
                """
                               <workflow xmlns:x=""/>
                               """, //
                """
                               <workflow xmlns:x="/nop/schema/xdsl.xdef"/>
                               """, //
                """
                               <workflow xmlns:xdsl="/nop/schema/xdsl.xdef"/>
                               """, //
                """
                               <workflow xmlns:xdsl=""/>
                               """, //
                """
                               <workflow xmlns:xdsl="/nop/schema/xdsl.xdef" x:schema=""/>
                               """, //
        };
        for (String text : samples) {
            assertFileType("a.wf.xml", text, XmlFileType.INSTANCE);
        }

        samples = new String[] {
                """
                               <workflow x:schema=""/>
                               """, //
                """
                               <workflow x:schema=/>
                               """, //
                """
                               <workflow x:schema="/nop/schema/wf/wf.xdef">
                               </workflow>
                               """, //
                """
                               <workflow xmlns:x="/nop/schema/xdsl.xdef" x:schema="">
                               </workflow>
                               """, //
                """
                               <workflow xmlns:x="" x:schema=""/>
                               """, //
                """
                               <workflow xmlns:xdsl="/nop/schema/xdsl.xdef" xdsl:schema="">
                               </workflow>
                               """, //
                """
                               <workflow xmlns:xdsl="" xdsl:schema=""/>
                               """, //
        };
        for (String text : samples) {
            assertFileType("b.wf.xml", text, XLangFileType.INSTANCE);
        }
    }

    private void assertFileType(String fileName, String text, LanguageFileType expectedFileType) {
        PsiFile file = myFixture.configureByText(fileName, text);

        Assert.assertEquals(expectedFileType, file.getFileType());
    }
}
