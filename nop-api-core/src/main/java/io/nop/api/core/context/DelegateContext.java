package io.nop.api.core.context;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class DelegateContext implements IContext {
    private final IContext context;

    public DelegateContext(IContext context) {
        this.context = context;
    }

    @Override
    public boolean isClosed() {
        return context.isClosed();
    }

    @Override
    public String getTraceId() {
        return context.getTraceId();
    }

    @Override
    public void setTraceId(String traceId) {
        context.setTraceId(traceId);
    }

    @Override
    public String getTenantId() {
        return context.getTenantId();
    }

    @Override
    public void setTenantId(String tenantId) {
        context.setTenantId(tenantId);
    }

    @Override
    public String getDynAppId() {
        return context.getDynAppId();
    }

    @Override
    public void setDynAppId(String dynAppId) {
        context.setDynAppId(dynAppId);
    }

    @Override
    public String getUserId() {
        return context.getUserId();
    }

    @Override
    public void setUserId(String userId) {
        context.setUserId(userId);
    }

    @Override
    public String getUserName() {
        return context.getUserName();
    }

    @Override
    public void setUserName(String userName) {
        context.setUserName(userName);
    }

    @Override
    public String getUserRefNo() {
        return context.getUserRefNo();
    }

    @Override
    public void setUserRefNo(String userRefNo) {
        context.setUserRefNo(userRefNo);
    }

    @Override
    public String getLocale() {
        return context.getLocale();
    }

    @Override
    public void setLocale(String locale) {
        context.setLocale(locale);
    }

    @Override
    public String getTimezone() {
        return context.getTimezone();
    }

    @Override
    public void setTimezone(String timezone) {
        context.setTimezone(timezone);
    }

    @Override
    public long getCallExpireTime() {
        return context.getCallExpireTime();
    }

    @Override
    public void setCallExpireTime(long expireTime) {
        context.setCallExpireTime(expireTime);
    }

    @Override
    public String getCallIp() {
        return context.getCallIp();
    }

    @Override
    public void setCallIp(String callIp) {
        context.setCallIp(callIp);
    }

    @Override
    public Map<String, Object> getPropagateRpcHeaders() {
        return context.getPropagateRpcHeaders();
    }

    @Override
    public void setPropagateRpcHeaders(Map<String, Object> propagateHeaders) {
        context.setPropagateRpcHeaders(propagateHeaders);
    }

    @Override
    public Map<String, Object> getAttrs() {
        return context.getAttrs();
    }

    @Override
    public Object getAttribute(String name) {
        return context.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        context.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        context.removeAttribute(name);
    }

    @Override
    public boolean removeAttribute(String name, Object value) {
        return context.removeAttribute(name, value);
    }

    @Override
    public Object getInternalContext() {
        return context.getInternalContext();
    }

    @Override
    public void runOnContext(Runnable task) {
        context.runOnContext(task);
    }

    @Override
    public <T> T executeWithContext(Callable<T> task) throws Exception {
        return context.executeWithContext(task);
    }

    @Override
    public void execute(Runnable task) {
        context.execute(task);
    }

    @Override
    public boolean isRunningOnContext() {
        return context.isRunningOnContext();
    }

    @Override
    public IContext getSourceContext() {
        return context.getSourceContext();
    }

    @Override
    public <T> CompletionStage<T> executeBlocking(Supplier<?> task, boolean ordered) {
        return context.executeBlocking(task, ordered);
    }

    @Override
    public void close() {
        context.close();
    }

    @Override
    public <T> T syncGet(CompletionStage<T> future) {
        return context.syncGet(future);
    }
}
