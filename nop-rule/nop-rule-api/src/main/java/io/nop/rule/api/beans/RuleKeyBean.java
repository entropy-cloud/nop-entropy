/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.api.beans;

import io.nop.rule.api.beans._gen._RuleKeyBean;

public class RuleKeyBean extends _RuleKeyBean {
    public RuleKeyBean() {
    }

    public RuleKeyBean(String ruleName) {
        setRuleName(ruleName);
    }
}
