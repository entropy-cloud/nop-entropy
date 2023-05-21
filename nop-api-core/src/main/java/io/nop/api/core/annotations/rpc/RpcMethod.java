package io.nop.api.core.annotations.rpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在RPC客户端接口方法上标注的扩展信息
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcMethod {
    String cancelMethod() default "";

    String pollingMethod() default "";

    int pollInterval() default -1;
}