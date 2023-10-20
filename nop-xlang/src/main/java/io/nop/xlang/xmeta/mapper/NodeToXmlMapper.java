/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.mapper;

import io.nop.api.core.beans.TreeBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.Map;

public class NodeToXmlMapper implements IObjPropMapper {

    @Override
    public Object mapTo(Object obj, IObjPropMeta propMeta, Object value) {
        if (StringHelper.isEmptyObject(value))
            return null;

        if (value instanceof XNode)
            return ((XNode) value).xml();

        return value.toString();
    }

    @Override
    public Object mapFrom(Object obj, IObjPropMeta propMeta, Object value) {
        if (StringHelper.isEmptyObject(value))
            return null;
        if (value instanceof XNode)
            return value;

        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            return XNode.fromTreeBean(TreeBean.createFromJson(map));
        }

        String text = value.toString();
        if (StringHelper.isBlank(text))
            return null;
        XNode node = XNodeParser.instance().parseFromText(null, text);
        if (node.getChildCount() == 1 && !node.isDummyNode())
            node = node.child(0);
        return node;
    }

}
