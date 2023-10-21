/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page.vue;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.xlang.ast.Expression;

import java.util.List;
import java.util.Map;

@DataBean
public class VueNode {
    private String type;
    private String ref;
    private Expression key;
    private Expression ifExpr;

    private Expression itemsExpr;
    private String indexVarName;
    private String itemVarName;
    private Map<String, Object> props;
    private List<VueNode> children;
    private Map<String, Expression> eventHandlers;

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