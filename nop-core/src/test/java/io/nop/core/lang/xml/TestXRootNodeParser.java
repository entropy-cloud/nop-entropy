/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml;

import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.core.lang.xml.parse.XRootNodeParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXRootNodeParser {
    @Test
    public void testRoot() {
        String text = "<a b='1'></b>";
        XNode node = new XRootNodeParser().parseFromReader(null, new CharSequenceReader(text));
        assertEquals("1", node.getAttr("b"));
    }
}
