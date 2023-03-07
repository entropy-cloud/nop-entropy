/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.xml;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.IXNodeHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JsonToXNodeTransformer {
    private final IJsonToXNodeAdapter adapter;

    public JsonToXNodeTransformer(IJsonToXNodeAdapter adapter) {
        this.adapter = adapter;
    }

    public void transform(Object obj, IXNodeHandler out) {
        transformNode(null, obj, out);
    }

    void transformNode(String key, Object obj, IXNodeHandler out) {
        NodeData data = adapter.getNodeData(null, obj);
        switch (data.getNodeType()) {
            case value: {
                out.value(null, data.getContent());
                break;
            }
            default: {
                String tagName = data.getTagName();
                if (tagName == null) {
                    tagName = CoreConstants.DUMMY_TAG_NAME;
                }
                if (data.hasChild()) {
                    out.beginNode(data.getLocation(), tagName, buildAttrs(data.getAttrs()));
                    adapter.enterBody(data);
                    transformChildren(data, out);
                    adapter.leaveBody(data);
                    out.endNode(tagName);
                } else if (data.getContent() != null) {
                    out.beginNode(data.getLocation(), tagName, buildAttrs(data.getAttrs()));
                    out.value(null, data.getContent());
                    out.endNode(tagName);
                } else {
                    out.simpleNode(data.getLocation(), tagName, buildAttrs(data.getAttrs()));
                }
            }
        }
    }

    Map<String, ValueWithLocation> buildAttrs(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty())
            return Collections.emptyMap();
        Map<String, ValueWithLocation> ret = CollectionHelper.newLinkedHashMap(attrs.size());
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            ret.put(entry.getKey(), ValueWithLocation.of(null, entry.getValue()));
        }
        return ret;
    }

    void transformChildren(NodeData data, IXNodeHandler out) {
        Map<String, Object> childrenMap = data.getChildrenMap();
        if (childrenMap != null) {
            for (Map.Entry<String, Object> entry : childrenMap.entrySet()) {
                transformNode(entry.getKey(), entry.getValue(), out);
            }
        }
        List<Object> children = data.getChildren();
        if (children != null) {
            for (Object child : children) {
                transformNode(null, child, out);
            }
        }
    }
}