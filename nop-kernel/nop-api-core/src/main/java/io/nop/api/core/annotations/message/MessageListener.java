package io.nop.api.core.annotations.message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface MessageListener {
    String[] topic();

    /**
     * 对应于bean容器中的消息源对象，要求实现IMessageSubscriber接口
     *
     * @return bean的id
     */
    String messageServiceBean();

    /**
     * 对应于bean容器中配置的MessageSubscribeOptions对象
     *
     * @return bean的id
     */
    String subscribeOptionsBean() default "";
}
