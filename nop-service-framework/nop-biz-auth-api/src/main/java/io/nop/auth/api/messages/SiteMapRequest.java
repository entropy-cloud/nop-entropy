/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.messages;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

@DataBean
public class SiteMapRequest {
    private String siteId;
    private boolean includeFunctionPoints;

    public SiteMapRequest() {
    }

    public SiteMapRequest(String siteId) {
        this.siteId = siteId;
    }

    @PropMeta(propId = 1)
    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    @PropMeta(propId = 2)
    public boolean isIncludeFunctionPoints() {
        return includeFunctionPoints;
    }

    public void setIncludeFunctionPoints(boolean includeFunctionPoints) {
        this.includeFunctionPoints = includeFunctionPoints;
    }
}
