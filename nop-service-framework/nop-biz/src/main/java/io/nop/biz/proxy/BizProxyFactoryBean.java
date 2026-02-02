package io.nop.biz.proxy;

import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.util.Guard;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import jakarta.inject.Inject;

public class BizProxyFactoryBean {
    private IBizObjectManager bizObjectManager;
    private String bizObjName;

    @Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    public void setBizObjName(String bizObjName) {
        this.bizObjName = bizObjName;
    }

    @BeanMethod
    public Object build() {
        Guard.notEmpty(bizObjName, "bizObjName");

        IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
        return bizObj.asProxy();
    }
}
