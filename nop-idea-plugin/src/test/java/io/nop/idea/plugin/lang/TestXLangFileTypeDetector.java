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

    public void testXLangFileType() {
        assertFileType("my.wf.xml", //
                       """
                               <workflow x:schema="/nop/schema/wf/wf.xdef">
                               </workflow>
                               """, //
                       XmlFileType.INSTANCE //
        );

        assertFileType("xlib.register-model.xml", //
                       """
                               <model xmlns:x="/nop/schema/xdsl.xdef"
                                      x:schema="/nop/schema/register-model.xdef"
                                      name="xlib"
                               >
                                   <loaders>
                                       <xdsl-loader fileType="xlib" schemaPath="/nop/schema/xlib.xdef"/>
                                   </loaders>
                               </model>
                               """, //
                       XLangFileType.INSTANCE //
        );
    }

    private void assertFileType(String fileName, String text, LanguageFileType expectedFileType) {
        PsiFile file = myFixture.configureByText(fileName, text);

        Assert.assertEquals(expectedFileType, file.getFileType());
    }
}
