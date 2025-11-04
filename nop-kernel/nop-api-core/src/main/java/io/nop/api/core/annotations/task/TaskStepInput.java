package io.nop.api.core.annotations.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskStepInput {
    String name() default "";

    String source() default "";

    boolean fromTaskScope() default false;

    String displayName() default "";

    boolean mandatory() default false;

    boolean persist() default true;

    String description() default "";

    String role() default "";
}