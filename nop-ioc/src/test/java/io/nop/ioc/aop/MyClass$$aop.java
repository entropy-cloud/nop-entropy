/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.aop;

import io.nop.api.core.annotations.txn.Transactional;
import io.nop.commons.util.ClassHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.core.reflect.aop.IMethodInvocation;
import io.nop.core.reflect.bean.IBeanModel;

import java.io.IOException;
import java.util.concurrent.Callable;

public class MyClass$$aop extends io.nop.ioc.aop.MyClass implements io.nop.core.reflect.aop.IAopProxy {
    private io.nop.core.reflect.aop.IMethodInterceptor[] $$interceptors;

    public MyClass$$aop(String data) throws IOException {
        super(data);
    }

    @Override
    public void $$aop_interceptors(io.nop.core.reflect.aop.IMethodInterceptor[] interceptors) {
        this.$$interceptors = interceptors;
    }

    static io.nop.core.reflect.IFunctionModel $$myMethod_0;

    static {
        try {
            $$myMethod_0 = io.nop.core.reflect.impl.MethodModelBuilder
                    .from(MyClass.class, io.nop.ioc.aop.MyClass.class.getDeclaredMethod("myMethod", java.lang.String.class, int.class,
                            byte[].class, java.util.List[].class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void __default_myMethod(java.lang.String arg0, int arg1, byte[] arg2, java.util.List[] arg3)
            throws java.io.IOException {
        super.myMethod(arg0, arg1, arg2, arg3);
    }

    @Override
    public void myMethod(java.lang.String arg0, int arg1, byte[] arg2, java.util.List[] arg3)
            throws java.io.IOException {
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            super.myMethod(arg0, arg1, arg2, arg3);
            return;
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(
                this, new java.lang.Object[]{arg0, arg1, arg2, arg3}, $$myMethod_0, new Callable() {
            @Override
            public Object call() throws Exception {
                __default_myMethod(arg0, arg1, arg2, arg3);
                return null;
            }
        });

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv,
                this.$$interceptors);
        try {
            $$inv.proceed();
        } catch (java.io.IOException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }

    public static void main(String[] args) throws IOException {
        MyClass$$aop aop = new MyClass$$aop("s");
        aop.$$aop_interceptors(new IMethodInterceptor[]{new IMethodInterceptor() {
            @Override
            public Object invoke(IMethodInvocation inv) throws Exception {
                Transactional a = inv.getMethod().getAnnotation(Transactional.class);
                System.out.println("class=" + a.getClass());

                IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(ClassHelper.getObjClass(a));
                System.out.println(beanModel.getProperty(a, "txnGroup"));

                System.out.println("before");
                return inv.proceed();
            }
        }});
        aop.myMethod("s", 1, null, null);
    }
}
