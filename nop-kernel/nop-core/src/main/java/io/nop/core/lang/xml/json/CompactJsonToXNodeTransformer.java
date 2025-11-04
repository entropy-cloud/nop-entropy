/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.xml.json;

import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.IObjectToXNodeTransformer;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompactJsonToXNodeTransformer implements IObjectToXNodeTransformer {

    @Override
    public XNode transformToXNode(Object value) {
        if (value instanceof Map) {
            return transformMap((Map<String, Object>) value);
        } else {
            XNode node = XNode.make(CoreConstants.DUMMY_TAG_NAME);
            node.content(value);
            return node;
        }
    }

    private XNode transformMap(Map<String, Object> map) {
        String tagName = CoreConstants.DUMMY_TAG_NAME;
        String type = (String) map.get(CoreConstants.PROP_TYPE);
        if (!StringHelper.isEmpty(type)) {
            tagName = type;
        }

        // Object simples = map.get(CoreConstants.TAG_NAME_J_SIMPLE);
        // if (simples != null) {
        // simpleTags.addAll(ConvertHelper.toCsvSet(simples, NopEvalException::new));
        // }

        XNode node = XNode.make(tagName);
        map.forEach((key, value) -> {
            if (key.equals(CoreConstants.PROP_TYPE))
                return;

            // if (processSpecial(key, value))
            // return;

            if (isSimpleValue(value)) {
                // if (simpleTags.contains(key)) {
                // XNode child = XNode.make(key);
                // child.content(value);
                // node.appendChild(child);
                // } else {
                node.setAttr(key, value);
                // }
            } else if (value instanceof Map) {
                // select: {type:'input'} 转换为 <select><input/></select>
                // select: {text:'a'} 转换为 <select text="a" />
                // 也就是说type属性总是倾向于对应于tagName
                XNode child = transformMap((Map<String, Object>) value);
                if (child.getTagName().equals(CoreConstants.DUMMY_TAG_NAME)) {
                    child.setTagName(key);
                } else {
                    XNode wrap = XNode.make(key);
                    wrap.appendChild(child);
                    child = wrap;
                }
                node.appendChild(child);
            } else if (value instanceof List) {
                List<XNode> children = transformList((List<Object>) value);
                XNode child = XNode.make(key);
                child.setAttr(CoreConstants.ATTR_J_LIST, true);
                child.appendChildren(children);
                node.appendChild(child);
            }
        });
        return node;
    }

    private List<XNode> transformList(List<Object> list) {
        List<XNode> ret = new ArrayList<>(list.size());
        for (Object item : list) {
            XNode child = transformToXNode(item);
            ret.add(child);
        }
        return ret;
    }

    // private boolean processSpecial(String key, Object value) {
    // if (key.equals(CoreConstants.TAG_NAME_J_SIMPLE)) {
    // return true;
    // }
    // return false;
    // }

    private boolean isSimpleValue(Object value) {
        if (value instanceof Map)
            return false;
        if (value instanceof List)
            return false;
        return true;
    }
}
