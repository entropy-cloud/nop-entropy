/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.autotest;

import io.nop.api.core.annotations.core.OptionalBoolean;

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
     * 是否强制设置nop.datasource.jdbc-url为h2内存数据库。SnapshotTest设置为Checking的时候总是强制使用localDb运行，这里的配置无效。
     */
    boolean localDb() default false;

    /**
     * 是否自动根据ORM模型定义初始化数据库表结构。如果是快照验证阶段，则缺省为true。但是这里可以强制覆盖这个行为
     */
    OptionalBoolean initDatabaseSchema() default OptionalBoolean.NOT_SET;

    /**
     * 启用nop-config模块的Config管理机制
     */
    OptionalBoolean enableConfig() default OptionalBoolean.NOT_SET;

    /**
     * 启用NopIoc容器, 如果不设置，则平台内置为true
     */
    OptionalBoolean enableIoc() default OptionalBoolean.NOT_SET;

    OptionalBoolean enableActionAuth() default OptionalBoolean.NOT_SET;

    OptionalBoolean enableDataAuth() default OptionalBoolean.NOT_SET;

    /**
     *  RECORDING模式下会录制每个测试方法的执行结果，CHECKING模式下会验证录制结果与实际执行结果相匹配
     */
    SnapshotTest snapshotTest() default SnapshotTest.CHECKING;

    /**
     * 为单元测试指定的beans配置文件
     */
    String testBeansFile() default "";

    /**
     * 为单元测试指定的config配置文件
     */
    String testConfigFile() default "";

    /**
     * 是否使用测试专用时钟。测试专用时钟总是向前执行，而且每次调用都返回不同的时间
     */
    boolean useTestClock() default true;
}