/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.web.page.WebDynamicFileProcessor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
@NopTestConfig(localDb = true)
public class TestDynamicJsGen extends JunitBaseTestCase {
    @Test
    public void testGenerateCss() {
        String source = "/* @generate\n" +
                "  <ofd-gen:GenerateCss xpl:lib=\"/nop/web/xlib/ofd-gen.xlib\" modelPath=\"/nop/wf/designer/oa-flow.graph-designer.xml\" />\n" +
                "*/";
        String code = new WebDynamicFileProcessor().process(null, source);
        System.out.println(code);
    }

    @Test
    public void testGenerateJs() {
        String source = "/* @generate\n" +
                "  <ofd-gen:GenerateJs xpl:lib=\"/nop/web/xlib/ofd-gen.xlib\" modelPath=\"/nop/wf/designer/oa-flow.graph-designer.xml\" />\n" +
                "*/";
        String code = new WebDynamicFileProcessor().process(null, source);
        System.out.println(code);
    }
}
