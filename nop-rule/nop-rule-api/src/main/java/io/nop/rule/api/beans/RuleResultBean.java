/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.SourceLocation;

import java.util.List;

@DataBean
public class RuleResultBean {
    /**
     * 规则节点的源码位置，用于调试
     */
    private SourceLocation location;

    /**
     * 规则节点的名称，在整个规则的执行上下文中应该是唯一的。 如果发现ruleName对应的结果已经存在，则会抛出异常。
     */
    private String ruleName;

    /**
     * 输出的日志消息
     */
    private String message;

    /**
     * 规则节点是否匹配成功
     */
    private boolean matchResult;

    /**
     * 节点匹配成功后的输出
     */
    private List<RuleOutputBean> outputs;

    /**
     * 对应于规则节点在节点树中的index。只有叶子节点才具有index。在决策表的实现中，它对应于决策表行或者列的下标。
     */
    private int leafIndex = -1;

    public int getLeafIndex() {
        return leafIndex;
    }

    public void setLeafIndex(int leafIndex) {
        this.leafIndex = leafIndex;
    }

    public boolean isMatchResult() {
        return matchResult;
    }

    public void setMatchResult(boolean matchResult) {
        this.matchResult = matchResult;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<RuleOutputBean> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<RuleOutputBean> outputs) {
        this.outputs = outputs;
    }
}
