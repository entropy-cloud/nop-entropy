package io.nop.api.core.annotations.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskFlags {
    String match() default "";

    String[] enable() default {};

    String[] disable() default {};

    String rename() default "";
}