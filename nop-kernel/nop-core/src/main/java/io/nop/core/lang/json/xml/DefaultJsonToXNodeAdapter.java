/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.xml;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;

import java.util.Collection;
import java.util.Map;

public class DefaultJsonToXNodeAdapter implements IJsonToXNodeAdapter {

    @Override
    public NodeData getNodeData(String key, Object obj) {
        if (obj instanceof Map) {
            return getNodeDataFromMap(key, (Map<String, Object>) obj);
        } else if (obj instanceof Collection) {
            NodeData data = new NodeData();
            data.setNodeType(JsonXNodeType.list);
            data.setTagName(key);
            data.setChildren(CollectionHelper.toList(obj));
            return data;
        } else {
            return makeValue(key, obj);
        }
    }

    NodeData getNodeDataFromMap(String key, Map<String, Object> map) {
        NodeData data = new NodeData();
        data.setTagName(key);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (name.charAt(0) == CoreConstants.SLOT_NAME_PREFIX) {
                data.addNamedChild(name.substring(1), value);
            } else if (name.equals(ApiConstants.TREE_BEAN_PROP_TYPE)) {
                data.setTagName(StringHelper.toString(value, null));
            } else if (name.equals(ApiConstants.TREE_BEAN_PROP_BODY)) {
                if (value instanceof Collection) {
                    data.setChildren(CollectionHelper.toList(value));
                } else {
                    data.setContent(value);
                }
            } else if (name.equals(ApiConstants.TREE_BEAN_PROP_LOC)) {
                if (value instanceof Map) {
                    SourceLocation loc = SourceLocation.fromMap((Map<String, Object>) value);
                    data.setLocation(loc);
                } else if (value instanceof String) {
                    SourceLocation loc = SourceLocation.parse(value.toString());
                    data.setLocation(loc);
                }
            } else if (value instanceof Collection || value instanceof Map) {
                data.addNamedChild(name, value);
            } else {
                data.addAttr(name, value);
            }
        }
        return data;
    }

    NodeData makeValue(String key, Object value) {
        NodeData data = new NodeData();
        data.setTagName(key);
        data.setContent(value);
        data.setNodeType(JsonXNodeType.value);
        return data;
    }
}