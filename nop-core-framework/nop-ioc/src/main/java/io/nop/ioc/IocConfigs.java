/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

public interface IocConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(IocConfigs.class);
    @Description("应用Ioc容器的启动模式")
    IConfigReference<String> CFG_IOC_APP_BEANS_CONTAINER_START_MODE = AppConfig
            .varRef(S_LOC, "nop.ioc.app-beans-container.start-mode", String.class, null);

    @Description("是否初始化IoC容器")
    IConfigReference<Boolean> CFG_IOC_APP_BEANS_CONCURRENT_START = AppConfig.varRef(S_LOC, "nop.ioc.app-beans-container.concurrent-start", Boolean.class, false);

    @Description("是否自动装载所有模块下的beans/app-*.beans.xml文件")
    IConfigReference<Boolean> CFG_IOC_APP_BEANS_FILE_ENABLED = AppConfig.varRef(S_LOC, "nop.ioc.app-beans-file.enabled",
            Boolean.class, true);

    @Description("缺省情况下所有模块下的beans/app-*.beans.xml文件都被被装载，这里可以设置过滤条件，从而只装载一部分bean配置")
    IConfigReference<String> CFG_IOC_APP_BEANS_FILE_PATTERN = AppConfig.varRef(S_LOC, "nop.ioc.app-beans-file.pattern",
            String.class, null);

    @Description("缺省情况下所有模块下的beans/app-*.beans.xml文件都被被装载，这里可以设置过滤条件，从而只装载一部分bean配置")
    IConfigReference<String> CFG_IOC_APP_BEANS_FILE_SKIP_PATTERN = AppConfig
            .varRef(S_LOC, "nop.ioc.app-beans-file.skip-pattern", String.class, null);

    @Description("是否启用自动配置机制。如果启用，则自动装载/nop/auto-config/*.beans中指定的所有beans.xml文件")
    IConfigReference<Boolean> CFG_IOC_AUTO_CONFIG_ENABLED = AppConfig.varRef(S_LOC, "nop.ioc.auto-config.enabled",
            Boolean.class, true);

    @Description("是否启用beans文件优化机制。如果启用，则首先检查是否存在/nop/main/beans/merged-app.beans.xml文件，如果存在，则跳过所有其他的beans文件解析")
    IConfigReference<Boolean> CFG_IOC_MERGED_BEANS_FILE_ENABLED = AppConfig.varRef(S_LOC, "nop.ioc.merged-beans-file.enabled",
            Boolean.class, true);

    @Description("是否启用AOP机制")
    IConfigReference<Boolean> CFG_IOC_AOP_ENABLED = AppConfig.varRef(S_LOC, "nop.ioc.aop.enabled", Boolean.class, true);

    @Description("如果不为空，且启用auto-config的情况下，则只启用指定的xxx.beans配置")
    IConfigReference<String> CFG_IOC_AUTO_CONFIG_PATTERN = AppConfig.varRef(S_LOC, "nop.ioc.auto-config.pattern", String.class,
            null);

    @Description("如果不为空，且启用auto-config的情况下，则排除指定的xxx.beans配置")
    IConfigReference<String> CFG_IOC_AUTO_CONFIG_SKIP_PATTERN = AppConfig.varRef(S_LOC, "nop.ioc.auto-config.skip-pattern",
            String.class, null);

    @Description("增加app容器的配置")
    IConfigReference<String> CFG_IOC_APP_BEANS_FILES = AppConfig.varRef(S_LOC, "nop.ioc.app-beans.files", String.class, null);

    @Description("允许bean的定义存在循环依赖关系")
    IConfigReference<Boolean> CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE =
            AppConfig.varRef(S_LOC, "nop.ioc.bean-depends-graph.allow-cycle", Boolean.class, true);
}