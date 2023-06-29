/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.autotest;

import io.nop.api.core.ioc.BeanContainerStartMode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface NopTestConfig {
    /**
     * 是否强制设置nop.datasource.jdbc-url为h2内存数据库
     */
    boolean localDb() default false;

    /**
     * 使用随机生成的服务端口
     */
    boolean randomPort() default false;

    /**
     * 缺省使用lazy模式来执行单元测试
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    String enableActionAuth() default "";

    String enableDataAuth() default "";

    /**
     * 是否自动加载/nop/auto-config/目录下的xxx.beans配置
     */
    boolean enableAutoConfig() default true;

    boolean enableMergedBeansFile() default true;

    String autoConfigPattern() default "";

    String autoConfigSkipPattern() default "";

    /**
     * 是否自动加载模块下的app.beans.xml配置
     */
    boolean enableAppBeansFile() default true;

    String appBeansFilePattern() default "";

    String appBeansFileSkipPattern() default "";

    /**
     * 为单元测试指定的beans配置文件
     */
    String testBeansFile() default "";

    /**
     * 为单元测试指定的config配置文件
     */
    String testConfigFile() default "";

    boolean initDatabaseSchema() default false;
}