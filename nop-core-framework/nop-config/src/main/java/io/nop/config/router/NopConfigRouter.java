/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.router;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * 配置灰度发布的路由规则
 */
@DataBean
public class NopConfigRouter {
    private boolean enabled = true;

    private List<NopConfigRoute> routes;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<NopConfigRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<NopConfigRoute> routes) {
        this.routes = routes;
    }
}
