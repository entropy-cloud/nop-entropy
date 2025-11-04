package io.nop.api.core.annotations.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskStepOutput {
    String name() default "";

    String source() default "";

    boolean toTaskScope() default false;

    String exportAs() default "";

    String displayName() default "";

    boolean persist() default true;

    String description() default "";

    String[] roles() default {};

    Class<?> type() default Object.class;
}
