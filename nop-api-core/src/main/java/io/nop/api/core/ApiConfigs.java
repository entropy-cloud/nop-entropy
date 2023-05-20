/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface ApiConfigs {
    @Description("当前激活的profile")
    IConfigReference<String> CFG_PROFILE =
            varRef("nop.profile", String.class, null);

    @Description("当前profile的父配置。配置查找顺序为 profile --> profile.parent --> standard")
    IConfigReference<String> CFG_PROFILE_PARENT =
            varRef("nop.profile.parent", String.class, null);

    @Description("在NopException中记录的xpl堆栈的最大深度")
    IConfigReference<Integer> CFG_EXCEPTION_MAX_XPL_STACK_SIZE =
            varRef("nop.exceptions.max-xpl-stack-size", Integer.class, 10);

    @Description("EvalException是否需要填充异常堆栈，一般情况下已经使用xplStack来记录XLang堆栈，没有必要再保留java堆栈")
    IConfigReference<Boolean> CFG_EXCEPTION_FILL_STACKTRACE =
            varRef("nop.exceptions.eval-exception-fill-stacktrace", Boolean.class, false);

    @Description("开启调试模式")
    IConfigReference<Boolean> CFG_DEBUG =
            varRef("nop.debug", Boolean.class, false);

    @Description("每一个启动的Nop应用程序应该指定一个唯一名称用于区分")
    IConfigReference<String> CFG_APPLICATION_NAME =
            varRef("nop.application.name", String.class, "nop-app");


    @Description("本应用程序的版本号")
    IConfigReference<String> CFG_APPLICATION_VERSION =
            varRef("nop.application.version", String.class, "");


    @Description("应用程序的多语言设置")
    IConfigReference<String> CFG_APPLICATION_LOCALE =
            varRef("nop.application.locale", String.class, "zh-CN");

    @Description("配置文件中的缺省多语言设置")
    IConfigReference<String> CFG_DEFAULT_LOCALE =
            varRef("nop.default-locale", String.class, "zh-CN");

    @Description("应用程序的缺省时区，所有LocalDate, LocateDateTime等都对应这个时区设置，因此存储到数据库中也对应这个时区")
    IConfigReference<String> CFG_APPLICATION_TIMEZONE =
            varRef("nop.application.timezone", String.class, null);

    @Description("应用程序在配置中心对应的名字空间")
    IConfigReference<String> CFG_APPLICATION_NAMESPACE =
            varRef("nop.application.namespace", String.class, "default");

    @Description("RPC调用时自动传播的header的名称，采用逗号分隔，例如nop-svc-route")
    IConfigReference<String> CFG_RPC_PROPAGATE_HEADERS =
            varRef("nop.rpc.propagate-headers", String.class, ApiConstants.HEADER_SVC_ROUTE);

}