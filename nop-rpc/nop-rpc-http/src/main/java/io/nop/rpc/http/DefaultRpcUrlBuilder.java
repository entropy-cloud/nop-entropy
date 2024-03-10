/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.http;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.rpc.core.utils.RpcHelper;

public class DefaultRpcUrlBuilder implements IRpcUrlBuilder {
    private final String baseUrl;

    public DefaultRpcUrlBuilder(String baseUrl) {
        this.baseUrl = normalize(baseUrl);
    }

    static String normalize(String baseUrl) {
        if (baseUrl == null) return "";
        if (baseUrl.endsWith("/")) return baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }

    @Override
    public String buildUrl(ApiRequest<?> req, String serviceMethod) {
        String url = RpcHelper.getHttpUrl(req);
        if (url == null) {
            if (serviceMethod.startsWith("/")) {
                url = baseUrl + serviceMethod;
            } else {
                url = baseUrl + "/r/" + serviceMethod;
            }
        } else {
            url = baseUrl + url;
        }
        url = appendSelection(url, req);
        return url;
    }

    private String appendSelection(String url, ApiRequest<?> req) {
        if (req.getSelection() != null) {
            String selection = req.getSelection().toString();
            if (!ApiStringHelper.isEmpty(selection)) {
                url = ApiStringHelper.appendQuery(url, ApiConstants.SYS_PARAM_SELECTION + "=" + ApiStringHelper.encodeURL(selection));
            }
        }
        return url;
    }
}