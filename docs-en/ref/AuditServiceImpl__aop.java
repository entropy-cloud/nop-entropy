package io.nop.auth.service.audit;

@io.nop.api.core.annotations.aop.AopProxy({io.nop.api.core.annotations.txn.Transactional.class})
public class AuditServiceImpl__aop extends io.nop.auth.service.audit.AuditServiceImpl implements io.nop.core.reflect.aop.IAopProxy{
    private io.nop.core.reflect.aop.IMethodInterceptor[] $$interceptors;

    @Override
    public void $$aop_interceptors(io.nop.core.reflect.aop.IMethodInterceptor[] interceptors) {
        this.$$interceptors = interceptors;
    }

    private static io.nop.core.reflect.IFunctionModel $$doProcess_0;

    static{
        try {
            $$doProcess_0 = io.nop.core.reflect.impl.MethodModelBuilder.from(io.nop.auth.service.audit.AuditServiceImpl.class.getDeclaredMethod("doProcess",java.util.List.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AuditServiceImpl__aop() {
        super();
    }

    private void __default_doProcess(final java.util.List arg0){
        super.doProcess(arg0); return;
    }

    @Override
    protected void doProcess(final java.util.List arg0){
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            super.doProcess(arg0); return;
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(this,
                new java.lang.Object[]{arg0}, $$doProcess_0, new java.util.concurrent.Callable(){
            @Override
            public Object call() throws Exception {
                __default_doProcess(arg0); return null;
            }
        });

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
        try {
            $$inv.proceed();
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }

}