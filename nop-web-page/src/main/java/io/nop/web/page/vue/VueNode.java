/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page.vue;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
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
    private Expression key;
    private Expression itemsExpr;
    private String indexVarName;
    private String itemVarName;

    /**
     * 同时包含v-for和v-if时，v-if在v-for之后执行
     */
    private Expression ifExpr;
    private Map<String, Object> props;
    private Map<String, Expression> eventHandlers;
    private Expression content;
    private List<VueNode> children;
    private Map<String, VueSlot> slots;

    public XNode toNode() {
        XNode node = XNode.make(type);
        node.setLocation(location);
        if (ref != null)
            node.setAttr(VueConstants.PROP_REF, ref);

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

        if (key != null) {
            node.setAttr(VueConstants.V_BIND_PREFIX + VueConstants.PROP_KEY, key.toExprString());
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

        if (content != null) {
            String text = TemplateExpression.toTemplateString(content, "{{", "}}");
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

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public Expression getContent() {
        return content;
    }

    public void setContent(Expression content) {
        this.content = content;
    }

    public Map<String, VueSlot> getSlots() {
        return slots;
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

    public Expression getKey() {
        return key;
    }

    public void setKey(Expression key) {
        this.key = key;
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