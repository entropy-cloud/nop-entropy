package io.nop.core.reflect.aop;

@io.nop.api.core.annotations.aop.AopProxy({io.nop.api.core.annotations.biz.BizMutation.class})
public class MyModel__aop extends io.nop.core.reflect.aop.TestAopCodeGenerator.MyModel implements io.nop.core.reflect.aop.IAopProxy {
    private io.nop.core.reflect.aop.IMethodInterceptor[] $$interceptors;

    @Override
    public void $$aop_interceptors(io.nop.core.reflect.aop.IMethodInterceptor[] interceptors) {
        this.$$interceptors = interceptors;
    }

    private static io.nop.core.reflect.IFunctionModel $$update_0;
    private static io.nop.core.reflect.IFunctionModel $$save_1;

    static {
        try {
            $$update_0 = io.nop.core.reflect.impl.MethodModelBuilder.from(io.nop.core.reflect.aop.TestAopCodeGenerator.MyModel.class, io.nop.core.reflect.aop.TestAopCodeGenerator.MyModel.class.getDeclaredMethod("update", io.nop.api.core.beans.query.QueryBean.class));
            $$save_1 = io.nop.core.reflect.impl.MethodModelBuilder.from(io.nop.core.reflect.aop.TestAopCodeGenerator.MyModel.class, io.nop.core.reflect.aop.TestAopCodeGenerator.BaseModel.class.getDeclaredMethod("save", java.lang.Object.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MyModel__aop() {
        super();
    }

    @Override
    public void update(final io.nop.api.core.beans.query.QueryBean arg0){
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            super.update(arg0); return;
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(this,
                new java.lang.Object[]{arg0}, $$update_0,() -> {
            super.update(arg0); return null;
        });

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
        try {
            $$inv.proceed();
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }

    @Override
    public io.nop.api.core.beans.query.QueryBean save(final io.nop.api.core.beans.query.QueryBean arg0){
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            return (io.nop.api.core.beans.query.QueryBean)super.save(arg0);
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(this,
                new java.lang.Object[]{arg0}, $$save_1,() -> {
            return (io.nop.api.core.beans.query.QueryBean)super.save(arg0);
        });

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
        try {
            return (io.nop.api.core.beans.query.QueryBean) $$inv.proceed();
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }

}