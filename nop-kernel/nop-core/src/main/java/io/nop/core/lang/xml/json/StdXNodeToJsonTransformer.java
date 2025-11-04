/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.json;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.handler.BuildJObjectJsonHandler;
import io.nop.core.lang.xml.IXNodeToObjectTransformer;
import io.nop.core.lang.xml.XNode;

import static io.nop.core.CoreConstants.TEXT_TAG_NAME;
import static io.nop.core.CoreConstants.XML_PROP_BODY;
import static io.nop.core.CoreConstants.XML_PROP_TYPE;

/**
 * 将XNode转换为json结构的标准方式。具体做法是将标签名转换为$type属性，将子节点转换为$body属性。 这一转换方式是双向可逆的，即可以根据json结构再转换回原先的XNode结构。
 */
public class StdXNodeToJsonTransformer implements IXNodeToObjectTransformer {
    private static final StdXNodeToJsonTransformer INSTANCE = new StdXNodeToJsonTransformer();

    public static StdXNodeToJsonTransformer instance() {
        return INSTANCE;
    }

    @Override
    public Object transformToObject(XNode node) {
        BuildJObjectJsonHandler handler = new BuildJObjectJsonHandler();
        transformToJson(node, handler);
        return handler.getResult();
    }

    public void transformToJson(XNode node, IJsonHandler json) {
        if (node.getTagName().equals(TEXT_TAG_NAME)) {
            ValueWithLocation vl = node.content();
            json.valueLoc(vl);
        } else {
            json.beginObject(node.getLocation());
            json.key(XML_PROP_TYPE).value(node.getLocation(), node.getTagName());
            node.forEachAttr((name, vl) -> {
                json.key(name).valueLoc(vl);
            });

            if (node.hasContent()) {
                json.key(XML_PROP_BODY).valueLoc(node.content());
            } else if (node.hasChild()) {
                json.key(XML_PROP_BODY);
                json.beginArray(node.getLocation());
                for (XNode child : node.getChildren()) {
                    transformToJson(child, json);
                }
                json.endArray();
            }
            json.endObject();
        }
    }

    public void transformBodyToJson(XNode node, IJsonHandler json) {
        if (node.hasChild()) {
            json.beginArray(node.getLocation());
            for (XNode child : node.getChildren()) {
                transformToJson(child, json);
            }
            json.endArray();
        } else if (node.hasContent()) {
            json.valueLoc(node.content());
        }
    }
}