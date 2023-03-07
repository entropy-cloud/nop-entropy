/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.util.List;

/**
 * 规则构成树形结构，当本节点条件匹配成功后才会执行下一个节点。
 */
@DataBean
public class TreeRuleBean extends ExtensibleBean implements ISourceLocationGetter, ISourceLocationSetter {

    private SourceLocation location;

    /**
     * RuleNode的唯一标识，如果设置了不为空，则它应该在整个rule结构中唯一，从而可以用在positiveDepends等集合中。
     */
    private String name;

    /**
     * 本规则节点匹配成功后打印的日志消息
     */
    private String message;

    private TreeBean filter;

    /**
     * 如果匹配了本节点，则输出内容
     */
    private List<RuleOutputBean> outputs;

    /**
     * 是否允许多个子分支都成功匹配，缺省只会有一个分支成功匹配。
     */
    private boolean multipleMatch;

    /**
     * 子分支列表。本节点匹配成功之后将会继续匹配子分支
     */
    private List<TreeRuleBean> children;

    /**
     * 决策树的叶子节点的下标
     */
    private int leafIndex;

    @JsonIgnore
    public int getLeafIndex() {
        return leafIndex;
    }

    public void setLeafIndex(int leafIndex) {
        this.leafIndex = leafIndex;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public List<RuleOutputBean> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<RuleOutputBean> outputs) {
        this.outputs = outputs;
    }

    public boolean isMultipleMatch() {
        return multipleMatch;
    }

    public void setMultipleMatch(boolean multipleMatch) {
        this.multipleMatch = multipleMatch;
    }

    public List<TreeRuleBean> getChildren() {
        return children;
    }

    public void setChildren(List<TreeRuleBean> children) {
        this.children = children;
    }

}
