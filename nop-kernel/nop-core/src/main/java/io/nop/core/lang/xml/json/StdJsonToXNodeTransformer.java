/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.json;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.IObjectToXNodeTransformer;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_JSON_VALUE_NOT_NODE;

public class StdJsonToXNodeTransformer implements IObjectToXNodeTransformer {
    public static final StdJsonToXNodeTransformer INSTANCE = new StdJsonToXNodeTransformer();

    @Override
    public XNode transformToXNode(Object value) {
        if (StringHelper.isEmptyObject(value))
            return null;

        if (value instanceof XNode)
            return (XNode) value;

        if (value instanceof Map) {
            return transformMap((Map<String, Object>) value);
        } else {
            throw new NopException(ERR_JSON_VALUE_NOT_NODE)
                    .param(ARG_VALUE, value);
        }
    }

    public XNode transformMap(Map<String, Object> json) {
        String type = (String) json.get(CoreConstants.XML_PROP_TYPE);
        if (type == null) {
            XNode node = XNode.makeTextNode();
            node.content(json);
            return node;
        } else {
            XNode node = XNode.make(type);
            json.forEach((key, value) -> {
                // 跳过$type和$body
                if (key.charAt(0) == '$')
                    return;

                node.setAttr(key, value);
            });

            Object body = json.get(CoreConstants.XML_PROP_BODY);
            if (body != null) {
                if (body instanceof Collection) {
                    Collection<Object> list = (Collection<Object>) body;
                    List<XNode> children = transformList(list);
                    node.appendChildren(children);
                } else {
                    node.content(body);
                }
            }
            return node;
        }
    }

    private List<XNode> transformList(Collection<Object> list) {
        List<XNode> ret = new ArrayList<>(list.size());
        for (Object item : list) {
            ret.add(transformToXNode(item));
        }
        return ret;
    }
}
