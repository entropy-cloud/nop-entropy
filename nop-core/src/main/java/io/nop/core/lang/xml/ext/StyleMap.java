/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.ext;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.IXNodeExtension;
import io.nop.core.lang.xml.XNode;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class StyleMap implements Serializable, IXNodeExtension {
    private static final long serialVersionUID = -4783641823860891447L;
    private static final String ATTR_STYLE = "style";

    private Map<String, String> values;

    public StyleMap(Map<String, String> values) {
        this.values = values;
    }

    public StyleMap() {
        this(new LinkedHashMap<>());
    }

    public String toString() {
        return StringHelper.encodeStringMap(values, ':', ';');
    }

    public static StyleMap parse(String s) {
        return new StyleMap(parseStyleString(s));
    }

    public static StyleMap getFromNode(XNode node) {
        return (StyleMap) node.getExtension(StyleMap.class.getName());
    }

    public static StyleMap makeFromNode(XNode node) {
        StyleMap ext = (StyleMap) node.getExtension(StyleMap.class.getName());
        if (ext == null) {
            ext = parse(node.attrText(ATTR_STYLE));
            node.setExtension(StyleMap.class.getName(), ext);
        }
        return ext;
    }

    public static Map<String, String> parseStyleString(String s) {
        if (s == null)
            return new LinkedHashMap<>();

        Map<String, String> map = StringHelper.parseStringMap(s, ':', ';');
        if (map == null)
            return new LinkedHashMap<>();
        return map;
    }

    @Override
    public void syncToNode(XNode node) {
        if (this.values.isEmpty()) {
            node.removeAttr(ATTR_STYLE);
        } else {
            String text = node.attrText(ATTR_STYLE);
            String str = this.toString();
            if (!Objects.equals(text, str)) {
                node.setAttr(ATTR_STYLE, str);
            }
        }
    }

    @Override
    public void syncFromNode(XNode node) {
        this.values = parseStyleString(node.attrText(ATTR_STYLE));
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    public String get(String key) {
        return values.get(key);
    }

    public void remove(String key) {
        values.remove(key);
    }

    public void removeAll(Collection<String> keys) {
        if (keys != null) {
            values.keySet().removeAll(keys);
        }
    }

    public Number getNumber(String key) {
        String value = values.get(key);
        if (value == null)
            return null;

        int i = value.length() - 1;
        for (; i >= 0; i--) {
            char c = value.charAt(i);
            if (StringHelper.isDigit(c)) {
                break;
            }
        }
        if (i != value.length() - 1)
            value = value.substring(0, i + 1);
        if (StringHelper.isBlank(value))
            return null;
        return StringHelper.parseNumber(value);
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public void add(String key, String value) {
        values.put(key, value);
    }

    public void addAll(Map<String, String> map) {
        if (map != null) {
            values.putAll(map);
        }
    }
}
