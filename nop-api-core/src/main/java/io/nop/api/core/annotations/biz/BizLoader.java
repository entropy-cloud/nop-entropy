/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.biz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标记GraphQL的Loader
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BizLoader {
    /**
     * 对应GraphQL的field名称
     */
    String value() default "";

    /**
     * 自动给GraphQL类型增加loader对应的字段。缺省情况下只是对已有字段增加loader逻辑，不会自动增加字段本身
     */
    boolean autoCreateField() default false;

    /**
     * 缺省情况下BizLoader是应用于当前的BizModel对象。如果直接指定forType
     * 则表示它是应用于指定的GraphQLObjectType类型
     */
    Class<?> forType() default Object.class;

    String forTypeName() default "";
}