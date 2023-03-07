/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml;

import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

public class TestFragmentsParser {
    @Test
    public void testFragments() {
        String text = " \n<web:GenPage view=\"NopAuthUserSubstitution.view.xml\" page=\"main\" xpl:lib=\"/nop/web/xlib/web.xlib\" /> \n";
        XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, text);
        node.dump();
    }
}
