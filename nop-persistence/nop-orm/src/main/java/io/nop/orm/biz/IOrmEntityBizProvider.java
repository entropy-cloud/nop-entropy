package io.nop.orm;

public interface IOrmEntityBizProvider {
    /**
     *  DynamicEntity会注入这个接口，通过它可以获取指定业务对象的Biz接口方法
     */
    <T extends IOrmEntityBiz> T getBiz(Class<T> bizClass);
}
