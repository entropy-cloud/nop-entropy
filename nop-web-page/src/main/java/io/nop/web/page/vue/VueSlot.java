/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.vue;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.ast.Expression;

import java.util.List;
import java.util.Map;

@DataBean
public class VueSlot implements IVueNode {
    private SourceLocation location;
    private String slotName;
    private String slotVar;

    private Map<String, String> slotVarMap;

    private Expression content;
    private List<VueNode> children;

    public XNode toNode() {
        XNode ret = XNode.make(VueConstants.TAG_TEMPLATE);
        ret.setLocation(location);
        ret.setAttr(VueConstants.V_SLOT_PREFIX + slotName, slotVar);
        if (content != null)
            ret.setContentValue(content.toExprString());
        if (children != null) {
            children.forEach(child -> {
                ret.appendChild(child.toNode());
            });
        }
        return ret;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public Expression getContentExpr() {
        return content;
    }

    public void setContent(Expression content) {
        this.content = content;
    }

    public String getSlotName() {
        return slotName;
    }

    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public String getSlotVar() {
        return slotVar;
    }

    public void setSlotVar(String slotVar) {
        this.slotVar = slotVar;
    }

    public Map<String, String> getSlotVarMap() {
        return slotVarMap;
    }

    public void setSlotVarMap(Map<String, String> slotVarMap) {
        this.slotVarMap = slotVarMap;
    }

    public List<VueNode> getChildren() {
        return children;
    }

    public void setChildren(List<VueNode> children) {
        this.children = children;
    }
}
