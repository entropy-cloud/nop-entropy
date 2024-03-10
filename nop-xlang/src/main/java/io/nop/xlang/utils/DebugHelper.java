/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugHelper {
    static final Logger LOG = LoggerFactory.getLogger(DebugHelper.class);

    public static Object v(SourceLocation loc, String prefix, String var, Object v) {
        LOG.info("{}:{}=>{},loc={}", prefix, var, v, loc);
        return v;
    }

    /**
     * 将node输出到文件File中，然后再重新解析，使得节点的location得到更新。当解析一些没有缩进的xml时，可以通过这个方法来实现格式重排，
     */
    public static XNode reparse(XNode node, IResource resource) {
        String xml = node.xml();
        ResourceHelper.writeText(resource, xml, StringHelper.ENCODING_UTF8);
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        return XNodeParser.instance().parseFromText(loc, xml);
    }
}