/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.vue;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.TemplateExpression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据简化的Vue模板语句解析得到的节点对象
 */
@DataBean
public class VueNode implements IVueNode, ISourceLocationGetter {
    private SourceLocation location;
    private String type;
    private String ref;
    private Expression keyExpr;
    private Expression itemsExpr;
    private String indexVarName;
    private String itemVarName;

    private Expression isExpr;

    /**
     * 同时包含v-for和v-if时，v-if在v-for之后执行
     */
    private Expression ifExpr;
    private Map<String, Object> props;
    private Map<String, Expression> eventHandlers;

    private Expression htmlExpr;
    private Expression contentExpr;
    private List<VueNode> children;
    private Map<String, VueSlot> slots;

    /**
     * 如果首字母大写或者包含-，则认为是组件
     */
    public boolean isComponent() {
        return Character.isUpperCase(type.charAt(0)) || type.indexOf('-') > 0;
    }

    public boolean isFragment() {
        return CoreConstants.DUMMY_TAG_NAME.equals(type);
    }

    /**
     * 如果首字母大写或者包含-，则认为是组件
     */
    public String getComponentName() {
        if (isComponent()) {
            if(type.indexOf('-') < 0)
                return type;
            return StringHelper.camelCase(type, '-', true);
        }
        return null;
    }

    public XNode toNode() {
        XNode node = XNode.make(type);
        node.setLocation(location);
        if (ref != null)
            node.setAttr(VueConstants.PROP_REF, ref);

        if (isExpr != null) {
            node.setAttr(VueConstants.V_BIND_IS, isExpr.toExprString());
        }

        if (itemsExpr != null) {
            String varName = itemVarName;
            if (varName == null)
                varName = "_";

            String expr;
            if (indexVarName == null) {
                expr = varName + " in " + itemsExpr.toExprString();
            } else {
                expr = "(" + varName + "," + indexVarName + ") in " + itemsExpr.toExprString();
            }
            node.setAttr(VueConstants.V_FOR, expr);
        }

        if (keyExpr != null) {
            node.setAttr(VueConstants.V_BIND_KEY, keyExpr.toExprString());
        }

        if (ifExpr != null) {
            node.setAttr(VueConstants.V_IF, ifExpr.toExprString());
        }

        if (props != null) {
            props.forEach((name, value) -> {
                if (value instanceof String) {
                    node.setAttr(name, value);
                } else {
                    String str = value instanceof Expression ? ((Expression) value).toExprString() : String.valueOf(value);
                    node.setAttr(VueConstants.V_BIND_PREFIX + name, str);
                }
            });
        }

        if (eventHandlers != null) {
            eventHandlers.forEach((event, handler) -> {
                node.setAttr(VueConstants.V_ON_PREFIX + event, handler.toExprString());
            });
        }

        if (htmlExpr != null) {
            node.setAttr(VueConstants.V_HTML, htmlExpr.toExprString());
        }

        if (contentExpr != null) {
            String text = TemplateExpression.toTemplateString(contentExpr, "{{", "}}");
            node.setContentValue(text);
        }

        if (children != null) {
            children.forEach(child -> {
                node.appendChild(child.toNode());
            });
        }

        if (slots != null) {
            slots.values().forEach(slot -> {
                node.appendChild(slot.toNode());
            });
        }

        return node;
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public VueNode getBodyNode() {
        if (!hasChild())
            return null;

        if (children.size() == 1) {
            return children.get(0);
        }

        VueNode vueNode = new VueNode();
        vueNode.setType(CoreConstants.DUMMY_TAG_NAME);
        vueNode.setChildren(children);
        return vueNode;
    }

    public Expression getIsExpr() {
        return isExpr;
    }

    public void setIsExpr(Expression isExpr) {
        this.isExpr = isExpr;
    }

    public Expression getHtmlExpr() {
        return htmlExpr;
    }

    public void setHtmlExpr(Expression htmlExpr) {
        this.htmlExpr = htmlExpr;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public Expression getContentExpr() {
        return contentExpr;
    }

    public void setContentExpr(Expression contentExpr) {
        this.contentExpr = contentExpr;
    }

    public Map<String, VueSlot> getSlots() {
        return slots;
    }

    public boolean hasSlot() {
        return slots != null && !slots.isEmpty();
    }

    public void setSlots(Map<String, VueSlot> slots) {
        this.slots = slots;
    }

    public void addProp(String name, Object value) {
        if (props == null)
            props = new LinkedHashMap<>();
        props.put(name, value);
    }

    public void addEventListener(String event, Expression listener) {
        if (eventHandlers == null)
            eventHandlers = new LinkedHashMap<>();
        eventHandlers.put(event, listener);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Expression getKeyExpr() {
        return keyExpr;
    }

    public void setKeyExpr(Expression keyExpr) {
        this.keyExpr = keyExpr;
    }

    public Expression getIfExpr() {
        return ifExpr;
    }

    public void setIfExpr(Expression ifExpr) {
        this.ifExpr = ifExpr;
    }

    public Expression getItemsExpr() {
        return itemsExpr;
    }

    public void setItemsExpr(Expression itemsExpr) {
        this.itemsExpr = itemsExpr;
    }

    public String getIndexVarName() {
        return indexVarName;
    }

    public void setIndexVarName(String indexVarName) {
        this.indexVarName = indexVarName;
    }

    public String getItemVarName() {
        return itemVarName;
    }

    public void setItemVarName(String itemVarName) {
        this.itemVarName = itemVarName;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props;
    }

    public List<VueNode> getChildren() {
        return children;
    }

    public void setChildren(List<VueNode> children) {
        this.children = children;
    }

    public Map<String, Expression> getEventHandlers() {
        return eventHandlers;
    }

    public void setEventHandlers(Map<String, Expression> eventHandlers) {
        this.eventHandlers = eventHandlers;
    }
}