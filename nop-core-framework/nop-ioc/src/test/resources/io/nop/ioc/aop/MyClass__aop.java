package io.nop.ioc.aop;

@io.nop.api.core.annotations.aop.AopProxy({io.nop.api.core.annotations.txn.Transactional.class})
public class MyClass__aop extends io.nop.ioc.aop.MyClass implements io.nop.core.reflect.aop.IAopProxy {
    private io.nop.core.reflect.aop.IMethodInterceptor[] $$interceptors;

    @Override
    public void $$aop_interceptors(io.nop.core.reflect.aop.IMethodInterceptor[] interceptors) {
        this.$$interceptors = interceptors;
    }

    private static io.nop.core.reflect.IFunctionModel $$myMethod_0;
    private static io.nop.core.reflect.IFunctionModel $$innerMethod_1;

    static {
        try {
            $$myMethod_0 = io.nop.core.reflect.impl.MethodModelBuilder.from(io.nop.ioc.aop.MyClass.class, io.nop.ioc.aop.MyClass.class.getDeclaredMethod("myMethod", java.lang.String.class, int.class, byte[].class, java.util.List[].class));
            $$innerMethod_1 = io.nop.core.reflect.impl.MethodModelBuilder.from(io.nop.ioc.aop.MyClass.class, io.nop.ioc.aop.MyClass.class.getDeclaredMethod("innerMethod"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MyClass__aop(final java.lang.String arg0) throws java.io.IOException {
        super(arg0);
    }

    @Override
    public void myMethod(final java.lang.String arg0, final int arg1, final byte[] arg2, final java.util.List[] arg3) throws java.io.IOException{
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            super.myMethod(arg0, arg1, arg2, arg3); return;
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(this,
                new java.lang.Object[]{arg0, arg1, arg2, arg3}, $$myMethod_0,() -> {
            super.myMethod(arg0, arg1, arg2, arg3); return null;
        });

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
        try {
            $$inv.proceed();
        } catch (java.io.IOException e){
            throw e;
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }

    @Override
    protected java.lang.String innerMethod(){
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            return super.innerMethod();
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(this,
                new java.lang.Object[]{}, $$innerMethod_1,() -> {
            return super.innerMethod();
        });

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
        try {
            return (java.lang.String) $$inv.proceed();
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }

}