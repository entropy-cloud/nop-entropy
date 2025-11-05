package io.nop.netty.handlers;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.IWithIdentifier;
import io.nop.core.reflect.bean.BeanTool;

public class DefaultRpcMessageAdapter implements IRpcMessageAdapter {
    static final String ID_NAME = "id";

    public static final DefaultRpcMessageAdapter INSTANCE = new DefaultRpcMessageAdapter();

    @Override
    public Object getRequestId(Object request) {
        if (request instanceof IWithIdentifier)
            return ((IWithIdentifier) request).get_id();
        if (request instanceof ApiRequest)
            return ApiHeaders.getId((ApiRequest) request);
        return BeanTool.getProperty(request, ID_NAME);
    }

    @Override
    public Object getResponseId(Object response) {
        if (response instanceof IWithIdentifier)
            return ((IWithIdentifier) response).get_id();
        if (response instanceof ApiResponse)
            return ApiHeaders.getRelId((ApiResponse) response);
        return BeanTool.getProperty(response, ID_NAME);
    }
}
