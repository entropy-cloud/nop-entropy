package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.lang.json.JsonTool;
import io.nop.gateway.core.context.IGatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jGatewayLogger implements IGatewayLogger {

    private static final Logger LOG = LoggerFactory.getLogger(Slf4jGatewayLogger.class);

    private int maxBodyLength = 4096;

    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    public void logRequest(ApiRequest<?> request, IGatewayContext ctx) {
        if (!LOG.isInfoEnabled())
            return;

        String path = ctx.getRequestPath();
        String method = ctx.getHttpMethod();
        Object data = request.getData();
        String body = serializeBody(data);

        LOG.info("nop.gateway.request:path={},method={},body={}", path, method, body);
    }

    @Override
    public void logResponse(ApiResponse<?> response, IGatewayContext ctx) {
        if (!LOG.isInfoEnabled())
            return;

        String path = ctx.getRequestPath();
        int status = response.getHttpStatus();
        Object data = response.getData();
        String body = serializeBody(data);

        LOG.info("nop.gateway.response:path={},status={},body={}", path, status, body);
    }

    @Override
    public void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx) {
        if (!LOG.isInfoEnabled())
            return;

        String path = ctx.getRequestPath();
        String body = serializeBody(aggregatedResponse);

        LOG.info("nop.gateway.streaming-response:path={},body={}", path, body);
    }

    @Override
    public void logError(Throwable exception, IGatewayContext ctx) {
        String path = ctx.getRequestPath();
        LOG.error("nop.gateway.error:path={}", path, exception);
    }

    private String serializeBody(Object data) {
        if (data == null)
            return "null";

        try {
            String json = JsonTool.serialize(data, false);
            if (json.length() > maxBodyLength) {
                return json.substring(0, maxBodyLength) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }
}
