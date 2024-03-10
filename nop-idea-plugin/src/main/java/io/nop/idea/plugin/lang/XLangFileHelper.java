/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.commons.util.CharSequenceHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XRootNodeParser;
import io.nop.xlang.xdsl.XDslKeys;

public class XLangFileHelper {
    public static String getSchemaFromContent(CharSequence content) {
        try {
            if (!CharSequenceHelper.startsWith(content, "<"))
                return null;

            XNode node = new XRootNodeParser().parseFromReader(null, new CharSequenceReader(content));
            XDslKeys keys = XDslKeys.of(node);
            return (String) node.getAttr(keys.SCHEMA);
        } catch (Exception e) {
            return null;
        }
    }
}
