/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.biz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标记GraphQL的对象类型
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface BizModel {
    /**
     * 对应GraphQL的type名称
     */
    String value();

    /**
     * 只继承指定的业务操作。当从基类继承的时候，有可能需要指定只继承指定的操作。
     */
    String[] inheritActions() default {};

    /**
     * 禁用指定的操作。当从基类的继承的时候，有可能需要排除某些方法
     */
    String[] disabledActions() default {};
}