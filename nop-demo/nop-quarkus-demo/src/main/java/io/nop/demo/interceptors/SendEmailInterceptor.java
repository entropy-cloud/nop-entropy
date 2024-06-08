package io.nop.demo.interceptors;

import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.core.reflect.aop.IMethodInvocation;
import io.nop.demo.biz.MyRequest;

public class SendEmailInterceptor implements IMethodInterceptor {

    @Override
    public Object invoke(IMethodInvocation inv) throws Exception {

        if (inv.getArguments().length <= 0)
            return inv.proceed();

        Object arg = inv.getArguments()[0];
        if (arg instanceof MyRequest) {
            System.out.println("sendEmail:message=" + ((MyRequest) arg).getMessage());
        }
        return inv.proceed();
    }
}