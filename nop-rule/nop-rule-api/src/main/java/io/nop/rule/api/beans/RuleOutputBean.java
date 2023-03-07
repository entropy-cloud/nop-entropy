/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.beans;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class RuleOutputBean {
    public static final String OP_APPEND = "append";

    /**
     * 输出变量的名称
     */
    private String name;

    /**
     * 由{@link io.nop.rule.api.IRuleRuntime}负责解释的输出动作。例如append可以表示追加到已有的结果变量集合中，而不是覆盖 同名的变量。
     */
    private String op;

    /**
     * 输出变量的值
     */
    private Object value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}