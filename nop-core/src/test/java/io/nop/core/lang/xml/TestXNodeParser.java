/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXNodeParser extends BaseTestCase {
    @ParameterizedTest
    @MethodSource
    public void runTest(IFile file) {
        XNode node = XNodeParser.instance().keepComment(true).parseFromResource(file);
        boolean html = file.getName().endsWith(".xhtml");
        String text = node.fullXml(true, false);

        System.out.println(text);

        IResource resultFile = attachmentResource("parse/" + StringHelper.replaceFileExt(file.getName(), ".result"));
        String resultText = ResourceHelper.readText(resultFile, StringHelper.ENCODING_UTF8);
        assertEquals(StringHelper.normalizeCRLF(resultText, false), StringHelper.normalizeCRLF(text, false));

        String compact = node.fullXml(false, false);
        XNode node2 = XNodeParser.instance().keepComment(true).parseFromText(null, compact);
        assertEquals(text, node2.fullXml(true, false));
        assertTrue(node.isXmlEquals(node2));
    }

    // 与参数化测试方法同名的静态方法作为参数工厂
    static Stream<IFile> runTest() {
        TestXNodeParser xs = new TestXNodeParser();
        return xs.attachmentResources("parse", true).stream()
                .filter(file -> file.getName().endsWith(".xml") || file.getName().endsWith(".xhtml"));
    }
}
