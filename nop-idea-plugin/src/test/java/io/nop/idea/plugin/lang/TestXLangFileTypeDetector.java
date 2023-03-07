/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.lang;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Assert;

public class TestXLangFileTypeDetector extends LightJavaCodeInsightFixtureTestCase {

    public void testXLangFileType() {
        PsiFile file = myFixture.configureByText("my.wf.xml", "<workflow x:schema=\"/nop/schema/wf/wf.xdef\"></workflow>");
        Assert.assertEquals(XmlFileType.INSTANCE, file.getFileType());
    }

}
