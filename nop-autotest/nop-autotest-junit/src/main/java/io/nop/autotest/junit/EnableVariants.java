package io.nop.autotest.junit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 启用variants目录下的测试数据
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ArgumentsSource(VariantsArgumentProvider.class)
@ParameterizedTest
public @interface EnableVariants {
    /**
     * 可以指定只运行某个Variant。如果不指定，则运行基础用例以及所有的Variant
     */
    String[] value() default {};
}
