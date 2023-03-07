/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.router;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;

@DataBean
public class NopConfigRoute {
    private TreeBean condition;

    private String routeName;

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public TreeBean getCondition() {
        return condition;
    }

    public void setCondition(TreeBean condition) {
        this.condition = condition;
    }
}
