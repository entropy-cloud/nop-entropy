package io.nop.http.api.client.rpc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiStringHelper;

import static io.nop.api.core.ApiErrors.ERR_API_NO_SERVICE_NAME_HEADER;

public class DefaultApiUrlBuilder implements IApiUrlBuilder {
    private final String baseUrl;

    public DefaultApiUrlBuilder(String baseUrl) {
        this.baseUrl = normalize(baseUrl);
    }

    static String normalize(String baseUrl) {
        if (baseUrl == null)
            return "";
        if (baseUrl.endsWith("/"))
            return baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }

    @Override
    public String buildUrl(ApiRequest<?> req, String serviceMethod) {

        if (req.getData() instanceof GraphQLRequestBean) {
            return baseUrl + "/graphql";
        } else {
            String svcName = ApiHeaders.getSvcName(req);
            if (ApiStringHelper.isEmpty(svcName))
                throw new NopException(ERR_API_NO_SERVICE_NAME_HEADER);

            String url = baseUrl + "/r/" + svcName + "__" + serviceMethod;
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