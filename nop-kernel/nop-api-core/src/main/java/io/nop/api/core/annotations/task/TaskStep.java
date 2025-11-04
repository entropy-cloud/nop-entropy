/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记TaskFlow的步骤定义
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskStep {
    String executor() default "";

    long timeout() default 0;

    String next() default "";

    String nextOnError() default "";

    String[] waitSteps() default {};

    String[] waitErrorSteps() default {};

    String when() default "";

    boolean concurrent() default false;

    String[] tagSet() default {};

    String name() default "";

    boolean sync() default false;

    boolean internal() default false;

    String errorName() default "";

    boolean runOnContext() default false;

    boolean ignoreResult() default false;

    boolean recordMetrics() default false;

    boolean saveState() default false;

    boolean useParentScope() default false;

    TaskStepInput[] inputs() default {};

    TaskStepOutput[] outputs() default {};
}