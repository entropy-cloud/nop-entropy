package io.nop.api.core.context;

public class CallExpireTimeProxyContext extends DelegateContext {
    private long callExpireTime;

    public CallExpireTimeProxyContext(IContext context) {
        super(context);
        this.callExpireTime = context.getCallExpireTime();
    }

    @Override
    public long getCallExpireTime() {
        return callExpireTime;
    }

    @Override
    public void setCallExpireTime(long callExpireTime) {
        this.callExpireTime = callExpireTime;
    }
}
