/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.auth.api.messages.SiteMapBean;
import io.nop.auth.api.messages.SiteMapRequest;

public interface SiteMapApi {
    /**
     * 返回用户可以访问的界面菜单项
     *
     * @param request 站点id。对于较大的系统可以分为多个子站点。
     * @return 菜单树
     */
    ApiRequest<SiteMapBean> getSiteMap(ApiRequest<SiteMapRequest> request);
}