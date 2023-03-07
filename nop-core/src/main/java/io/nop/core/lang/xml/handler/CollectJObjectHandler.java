/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.handler;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JArray;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.utils.SourceLocationHelper;
import io.nop.core.lang.utils.NestedProcessingState;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_KEY;
import static io.nop.core.CoreErrors.ARG_OLD_LOC;
import static io.nop.core.CoreErrors.ARG_PARENT;
import static io.nop.core.CoreErrors.ARG_PARENT_TYPE;
import static io.nop.core.CoreErrors.ARG_TAG_NAME;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_JSON_DUPLICATE_KEY;
import static io.nop.core.CoreErrors.ERR_XML_HANDLER_BEGIN_END_MISMATCH;
import static io.nop.core.CoreErrors.ERR_XML_TO_JSON_OUTPUT_NOT_SUPPORT_MIX_TEXT_NODE;
import static io.nop.core.CoreErrors.ERR_XML_TO_JSON_OUTPUT_ONLY_SUPPORT_SIMPLE_TEXT_NODE;

public class CollectJObjectHandler extends XNodeHandlerAdapter {
    private String comment;
    private final JArray root = new JArray();

    private final List<Object> stack = new ArrayList<>();
    private final NestedProcessingState state = new NestedProcessingState(50);

    /**
     * 节点为列表类型，例如<buttons j:type="list">...</buttons>
     */
    private static final int LIST_TYPE = 1; // 节点为列表类型

    /**
     * 节点具有其他属性，对应Map类型，例如 <button name="ss">...</button>
     */
    private static final int OBJECT_TYPE = 2;

    /**
     * 节点没有其他属性，是否简单节点情况待定。例如 <title>xxx</title>或者 <title><span>xxx</span></title>
     */
    private static final int OBJECT_SIMPLE_CHILD = 3; // 节点没有其他属性

    /**
     * 列表节点的子节点，且没有其他属性。例如<_>3</_>
     */
    private static final int LIST_SIMPLE_CHILD = 4; // 节点名为_，且没有其他属性

    /**
     * 节点名为body，且没有其他属性。需要被特殊识别处理。如果body只有一个子节点，则body对应Map类型。如果有多个子节点，则body对应List类型
     */
    private static final int OBJECT_BODY_CHILD = 5; // body子节点需要被特殊处理

    private static final int SIMPLE_VALUE = 6;

    public CollectJObjectHandler() {
        stack.add(root);
        state.push(LIST_TYPE);
    }

    @Override
    public void comment(String comment) {
        if (this.comment != null) {
            this.comment += "\n" + comment;
        } else {
            this.comment = comment;
        }
    }

    public Object processNode(String tagName, XNode node) {
        if (!hasObjAttr(node.attrValueLocs())) {
            if (!node.hasChild()) {
                return node.getContentValue();
            }
        }

        JObject obj = newObject(node.getLocation(), tagName);
        assignAttrs(obj, node.attrValueLocs());
        JArray parent = currentList();
        parent.add(obj);
        stack.add(obj);
        state.push(OBJECT_TYPE);

        for (XNode child : node.getChildren()) {
            child.process(this);
        }

        endNode(tagName);

        return getResult();
    }

    @Override
    public void appendChild(SourceLocation loc, String tagName, Map<String, Object> attrs) {
        // if (CoreConstants.DUMMY_TAG_NAME.equals(tagName)) {
        //
        // }
        super.appendChild(loc, tagName, attrs);
    }

    @Override
    public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        if (tagName.equals(CoreConstants.TEXT_TAG_NAME))
            throw new NopException(ERR_XML_TO_JSON_OUTPUT_NOT_SUPPORT_MIX_TEXT_NODE).loc(loc);

        switch (state.peek()) {
            case LIST_TYPE: {
                beginListItem(loc, tagName, attrs);
                break;
            }
            case LIST_SIMPLE_CHILD: {
                // 仅列表元素会产生
                TagInfo tagInfo = this.currentTagInfo();
                JObject obj = newObject(tagInfo.loc, tagInfo.tagName);
                JArray parent = (JArray) tagInfo.parent;
                parent.add(obj);
                stack.set(stack.size() - 1, obj);
                state.replaceTop(OBJECT_TYPE);
                beginObjectChild(loc, tagName, attrs);
                break;
            }

            case OBJECT_SIMPLE_CHILD: {
                TagInfo tagInfo = currentTagInfo();
                JObject obj = newObject(tagInfo.loc, null);
                JObject parent = (JObject) tagInfo.parent;
                parent.put(tagInfo.propName, obj);
                stack.set(stack.size() - 1, obj);
                state.replaceTop(OBJECT_TYPE);
                beginObjectChild(loc, tagName, attrs);
                break;
            }
            case OBJECT_TYPE: {
                beginObjectChild(loc, tagName, attrs);
                break;
            }
            case OBJECT_BODY_CHILD: {
                Object current = current();
                if (current instanceof TagInfo) {
                    TagInfo tagInfo = (TagInfo) current;
                    JArray body = newList(tagInfo.loc);
                    JObject parent = (JObject) tagInfo.parent;
                    parent.put(tagInfo.propName, body);
                    // state.replaceTop(LIST_TYPE);
                    stack.set(stack.size() - 1, body);
                }
                beginListItem(loc, tagName, attrs);
                break;
            }
        }
    }

    void beginListItem(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        boolean hasAttr = hasObjAttr(attrs);
        JArray parent = currentList();
        if (isListType(attrs)) {
            JArray list = newList(loc);
            state.push(LIST_TYPE);
            parent.add(list);
            stack.add(list);
        } else if (hasAttr) {
            JObject obj = newObject(loc, tagName);
            assignAttrs(obj, attrs);
            parent.add(obj);
            stack.add(obj);
            state.push(OBJECT_TYPE);
        } else {
            TagInfo tagInfo = new TagInfo();
            tagInfo.loc = loc;
            tagInfo.tagName = tagName;
            tagInfo.parent = parent;
            stack.add(tagInfo);
            state.push(LIST_SIMPLE_CHILD);
        }
    }

    void beginObjectChild(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        boolean hasAttr = hasObjAttr(attrs);
        JObject parent = currentObject();
        if (isListType(attrs)) {
            JArray list = newList(loc);
            parent.put(getPropName(tagName, attrs, true), list);
            state.push(LIST_TYPE);
            stack.add(list);
        } else if (hasAttr) {
            JObject obj = newObject(loc, null);
            assignAttrs(obj, attrs);
            String key = getPropName(tagName, attrs, false);
            Object ret = parent.putIfAbsent(key, obj);
            if (ret != null)
                throw new NopException(ERR_JSON_DUPLICATE_KEY).source(obj).param(ARG_KEY, key)
                        .param(ARG_PARENT_TYPE, parent.get(CoreConstants.PROP_TYPE))
                        .param(ARG_OLD_LOC, SourceLocationHelper.getPropLocation(parent, key));
            stack.add(obj);
            state.push(OBJECT_TYPE);
        } else {
            TagInfo tagInfo = new TagInfo();
            tagInfo.loc = loc;
            tagInfo.tagName = tagName;
            tagInfo.propName = getPropName(tagName, attrs, false);
            tagInfo.parent = parent;
            stack.add(tagInfo);
            state.push(tagName.equals(CoreConstants.PROP_BODY) ? OBJECT_BODY_CHILD : OBJECT_SIMPLE_CHILD);
        }
    }

    static class TagInfo {
        SourceLocation loc;
        String tagName;
        // 在父对象中的属性名，有可能是根据j:key指定的
        String propName;
        Object parent;
    }

    // String normalizeName(String name) {
    // if (name.startsWith("__:")) {
    // name = "..." + name.substring("__:".length());
    // }
    // name = StringHelper.replace(name, "_:", "$");
    // return name;
    // }

    String getPropName(String tagName, Map<String, ValueWithLocation> attrs, boolean isList) {
        String propName = getAttr(attrs, CoreConstants.ATTR_J_KEY);
        if (StringHelper.isEmpty(propName)) {
            propName = tagName;
        }

        if (isList) {
            boolean replace = CoreConstants.OVERRIDE_REPLACE.equals(getAttr(attrs, CoreConstants.ATTR_X_OVERRIDE));
            if (replace) {
                return "!" + propName;
            }
        }
        return propName;
    }

    JArray newList(SourceLocation loc) {
        JArray obj = new JArray();
        obj.setLocation(loc);
        obj.setComment(comment);
        comment = null;
        return obj;
    }

    JObject newObject(SourceLocation loc, String tagName) {
        JObject obj = new JObject();
        obj.setLocation(loc);
        obj.setComment(comment);
        comment = null;
        if (tagName != null && !tagName.equals(CoreConstants.DUMMY_TAG_NAME)) {
            obj.put(CoreConstants.PROP_TYPE, ValueWithLocation.of(loc, tagName));
        }
        return obj;
    }

    void assignAttrs(JObject obj, Map<String, ValueWithLocation> attrs) {
        for (Map.Entry<String, ValueWithLocation> entry : attrs.entrySet()) {
            String name = entry.getKey();
            if (name.equals(CoreConstants.ATTR_J_LIST)) {
                continue;
            }
            if (name.equals(CoreConstants.ATTR_J_KEY))
                continue;
            obj.put(name, normalizeValue(entry.getValue()));
        }
    }

    ValueWithLocation normalizeValue(ValueWithLocation vl) {
        Object value = vl.getValue();
        if (value instanceof String || value instanceof CDataText) {
            String text = value.toString();
            if (text.startsWith(CoreConstants.ESCAPED_ATTR_EXPR_PREFIX)
                    || text.startsWith(CoreConstants.ESCAPED_ATTR_JSON_PREFIX)) {
                SourceLocation loc = SourceLocationHelper.offset(vl.getLocation(), 1);
                return ValueWithLocation.of(loc, text.substring(1));
            }
            if (text.startsWith(CoreConstants.ATTR_JSON_PREFIX)) {
                text = text.substring(CoreConstants.ATTR_JSON_PREFIX.length());
                SourceLocation loc = SourceLocationHelper.offset(vl.getLocation(),
                        CoreConstants.ATTR_JSON_PREFIX.length());
                Object v = JsonTool.parseNonStrict(loc, text);
                return ValueWithLocation.of(loc, v);
            }
            if (text.startsWith(CoreConstants.ATTR_EXPR_PREFIX)) {
                text = text.substring(CoreConstants.ATTR_EXPR_PREFIX.length());
                SourceLocation loc = SourceLocationHelper.offset(vl.getLocation(),
                        CoreConstants.ATTR_EXPR_PREFIX.length());
                String str = text.trim();
                if (str.isEmpty())
                    return ValueWithLocation.of(loc, str);

                if (str.equals("true")) {
                    return ValueWithLocation.of(loc, true);
                }
                if (str.equals("false"))
                    return ValueWithLocation.of(loc, false);

                if (str.equals("null")) {
                    return ValueWithLocation.of(loc, null);
                }

                if (StringHelper.isNumber(str)) {
                    return ValueWithLocation.of(loc, StringHelper.parseNumber(str));
                }
            }
        }
        return vl;
    }

    JObject currentObject() {
        return (JObject) current();
    }

    JArray currentList() {
        return (JArray) current();
    }

    TagInfo currentTagInfo() {
        return (TagInfo) current();
    }

    /**
     * 判断节点是否有除了系统属性之外的其他属性
     */
    boolean hasObjAttr(Map<String, ValueWithLocation> attrs) {
        return attrs.size() > 1 || (attrs.size() == 1 && !attrs.containsKey(CoreConstants.ATTR_J_KEY));
        // if (attrs.isEmpty())
        // return false;
        //
        // int cnt = 0;
        // if (attrs.containsKey(CoreConstants.ATTR_J_TYPE)) {
        // cnt++;
        // }
        // if (attrs.containsKey(CoreConstants.ATTR_X_EXTENDS)) {
        // cnt++;
        // }
        // if (attrs.containsKey(CoreConstants.ATTR_X_OVERRIDE)) {
        // cnt++;
        // }
        // return attrs.size() != cnt;
    }

    boolean isListType(Map<String, ValueWithLocation> attrs) {
        return getAttrBoolean(attrs, CoreConstants.ATTR_J_LIST, false);
    }

    @Override
    public void value(SourceLocation loc, Object value) {
        if (value instanceof XNode) {
            ((XNode) value).process(this);
            return;
        }
        switch (state.peek()) {
            case LIST_SIMPLE_CHILD: {
                // 列表的子节点如果是值节点，则会忽略标签名。例 <options j:list="true"><option>1</option></options>实际会得到options:["1"]
                TagInfo tag = currentTagInfo();
                JArray parent = (JArray) tag.parent;
                parent.add(normalizeValue(ValueWithLocation.of(loc, value)));
                state.replaceTop(SIMPLE_VALUE);
                break;
            }
            case OBJECT_SIMPLE_CHILD:
            case OBJECT_BODY_CHILD: {
                TagInfo tag = currentTagInfo();
                JObject parent = (JObject) tag.parent;
                parent.put(tag.propName, normalizeValue(ValueWithLocation.of(loc, value)));
                break;
            }
            default:
                throw new NopException(ERR_XML_TO_JSON_OUTPUT_ONLY_SUPPORT_SIMPLE_TEXT_NODE).loc(loc)
                        .param(ARG_PARENT, current()).param(ARG_VALUE, value);
        }
    }

    @Override
    public void endNode(String tagName) {
        switch (state.peek()) {
            case OBJECT_BODY_CHILD: {
                Object current = current();
                if (current instanceof JArray) {
                    JArray list = (JArray) current;
                    if (list.size() == 1) {
                        JObject parent = (JObject) stack.get(stack.size() - 2);
                        parent.put(CoreConstants.PROP_BODY, list.get(0));
                    }
                }
                break;
            }
            case LIST_SIMPLE_CHILD: {
                TagInfo tagInfo = currentTagInfo();
                JArray list = (JArray) tagInfo.parent;
                JObject obj = newObject(tagInfo.loc, tagInfo.tagName);
                list.add(obj);
                break;
            }
        }

        if (stack.size() <= 1)
            throw new NopException(ERR_XML_HANDLER_BEGIN_END_MISMATCH).param(ARG_TAG_NAME, tagName);
        this.stack.remove(stack.size() - 1);
        this.state.pop();
    }

    @Override
    public void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        this.beginNode(loc, tagName, attrs);
        this.endNode(tagName);
    }

    public Object current() {
        return stack.get(stack.size() - 1);
    }

    @Override
    public XNode endDoc() {
        return null;
    }

    public Object getResult() {
        if (root.size() == 0)
            return null;
        if (root.size() == 1)
            return root.get(0);
        return root;
    }

    public List<Object> getList() {
        return root;
    }
}