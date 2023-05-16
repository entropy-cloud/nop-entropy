package io.nop.http.api.client.rpc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiStringHelper;

import java.util.function.BiFunction;

public class DefaultRpcUrlBuilder implements IRpcUrlBuilder {
    private final String baseUrl;
    private final BiFunction<ApiRequest<?>, String, Object> paramGetter;

    public DefaultRpcUrlBuilder(String baseUrl, BiFunction<ApiRequest<?>, String, Object> paramGetter) {
        this.baseUrl = normalize(baseUrl);
        this.paramGetter = paramGetter;
    }

    static String normalize(String baseUrl) {
        if (baseUrl == null)
            return "";
        if (baseUrl.endsWith("/"))
            return baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }

    @Override
    public String toHttpHeader(String name) {
        if (ApiConstants.HEADER_HTTP_METHOD.equals(name))
            return null;
        if (ApiConstants.HEADER_HTTP_URL.equals(name))
            return null;
        return name;
    }

    @Override
    public String buildUrl(ApiRequest<?> req, String serviceMethod) {
        String url = ApiHeaders.getHttpUrl(req);
        if (url != null) {
            url = ApiStringHelper.renderTemplate(url, key -> {
                return paramGetter.apply(req, key);
            });
            return url;
        }

        if (serviceMethod.startsWith("/"))
            return baseUrl + serviceMethod;

        if (req.getData() instanceof GraphQLRequestBean) {
            return baseUrl + "/graphql";
        } else {
            url = baseUrl + "/r/" + serviceMethod;
            if (req.getFieldSelection() != null) {
                String selection = req.getFieldSelection().toString();
                if (!ApiStringHelper.isEmpty(selection)) {
                    url = ApiStringHelper.appendQuery(url, ApiConstants.SYS_PARAM_SELECTION + "="
                            + ApiStringHelper.encodeURL(selection));
                }
            }
            return url;
        }
    }
}