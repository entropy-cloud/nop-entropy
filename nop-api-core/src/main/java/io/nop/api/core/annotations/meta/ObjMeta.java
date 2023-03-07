/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.meta;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 使用java的@Deprecated注解。
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface ObjMeta {

    String displayName() default "";

    String description() default "";

    /**
     * 最少有多少个属性
     */
    int minProperties() default 0;

    /**
     * 最多有多少个属性
     */
    int maxProperties() default Integer.MAX_VALUE;

    /**
     * 是否内部属性。内部属性不出现在IDE的提示列表中，一般情况下在界面上也不可见。
     */
    boolean internal() default false;

    /**
     * 转换为xml节点时对应的标签名，一般情况下与类名一致
     */
    String xmlName() default "";

    /**
     * 是否允许未定义的属性
     */
    boolean allowAnyAttr() default false;

    /**
     * 扩展验证条件，xml格式
     */
    String validate() default "";
}