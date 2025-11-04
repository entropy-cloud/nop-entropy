/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.output;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xpl.XplConstants;

public class HtmlOutputTagCompiler extends XmlOutputTagCompiler {
    public static final HtmlOutputTagCompiler INSTANCE = new HtmlOutputTagCompiler();

    @Override
    protected boolean isUseShortTagName(XNode node) {
        return XplConstants.HTML_SHORT_TAG_NAMES.contains(node.getTagName());
    }
}