/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.autotest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EnableSnapshot {

    /**
     * 如果启用了快照机制，则缺省会强制使用本地数据库，并且会使用录制的数据来初始化数据库。
     */
    boolean localDb() default true;

    /**
     * 是否自动执行input目录下的sql文件
     */
    boolean sqlInput() default true;

    boolean sqlInit() default true;

    /**
     * 是否自动将input/tables目录下的数据插入到数据库中
     */
    boolean tableInit() default true;

    /**
     * 是否将收集到的输出数据保存到结果目录下。当saveOutput=true时，checkOutput的设置将会被忽略
     */
    boolean saveOutput() default false;

    /**
     * 是否校验录制的输出数据与数据库中的当前数据相匹配
     */
    boolean checkOutput() default true;
}