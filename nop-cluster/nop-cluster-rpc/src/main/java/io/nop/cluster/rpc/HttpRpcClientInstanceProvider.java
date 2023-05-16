package io.nop.cluster.rpc;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.rpc.IRpcService;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.rpc.DefaultRpcUrlBuilder;
import io.nop.http.api.client.rpc.HttpRpcService;

import javax.inject.Inject;

public class HttpRpcClientInstanceProvider implements IRpcClientInstanceProvider {
    private IHttpClient httpClient;
    private boolean useHttps;

    public void setUseHttps(boolean useHttps) {
        this.useHttps = useHttps;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public IRpcService getRpcClientInstance(ServiceInstance instance) {
        String baseUrl = useHttps ? "https://" : "http://";
        baseUrl += instance.getAddr() + ":" + instance.getPort();
        return new HttpRpcService(httpClient, new DefaultRpcUrlBuilder(baseUrl, this::buildParam));
    }

    Object buildParam(ApiRequest<?> req, String key) {
        if (ApiConstants.SYS_PARAM_SELECTION.equals(key)) {
            FieldSelectionBean selectionBean = req.getFieldSelection();
            return selectionBean == null ? null : selectionBean.toString();
        }
        return BeanTool.getProperty(req, key);
    }
}