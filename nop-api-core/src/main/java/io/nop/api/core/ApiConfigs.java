/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.util.Set;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface ApiConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(ApiConfigs.class);

    @Description("当前激活的profile")
    IConfigReference<String> CFG_PROFILE =
            varRef(s_loc, "nop.profile", String.class, null);

    @Description("当前profile的父配置。配置查找顺序为 profile --> profile.parent --> standard")
    IConfigReference<Set> CFG_PROFILE_PARENT =
            varRef(s_loc, "nop.profile.parent", Set.class, null);

    @Description("在NopException中记录的xpl堆栈的最大深度")
    IConfigReference<Integer> CFG_EXCEPTION_MAX_XPL_STACK_SIZE =
            varRef(s_loc, "nop.exceptions.max-xpl-stack-size", Integer.class, 10);

    @Description("EvalException是否需要填充异常堆栈，一般情况下已经使用xplStack来记录XLang堆栈，没有必要再保留java堆栈")
    IConfigReference<Boolean> CFG_EXCEPTION_FILL_STACKTRACE =
            varRef(s_loc, "nop.exceptions.eval-exception-fill-stacktrace", Boolean.class, false);

    @Description("开启调试模式")
    IConfigReference<Boolean> CFG_DEBUG =
            varRef(s_loc, "nop.debug", Boolean.class, false);

    @Description("每次应用启动都会被分配唯一ID")
    IConfigReference<String> CFG_HOST_ID =
            varRef(s_loc, "nop.server.host-id", String.class, null);

    @Description("每一个启动的Nop应用程序应该指定一个唯一名称用于区分")
    IConfigReference<String> CFG_APPLICATION_NAME =
            varRef(s_loc, "nop.application.name", String.class, "nop-app");


    @Description("本应用程序的版本号")
    IConfigReference<String> CFG_APPLICATION_VERSION =
            varRef(s_loc, "nop.application.version", String.class, "");

    @Description("应用程序的多语言设置")
    IConfigReference<String> CFG_APPLICATION_LOCALE =
            varRef(s_loc, "nop.application.locale", String.class, "zh-CN");

    @Description("配置文件中的缺省多语言设置")
    IConfigReference<String> CFG_DEFAULT_LOCALE =
            varRef(s_loc, "nop.default-locale", String.class, "zh-CN");

    @Description("应用程序的缺省时区，所有LocalDate, LocateDateTime等都对应这个时区设置，因此存储到数据库中也对应这个时区")
    IConfigReference<String> CFG_APPLICATION_TIMEZONE =
            varRef(s_loc, "nop.application.timezone", String.class, null);

    @Description("应用程序在配置中心对应的名字空间")
    IConfigReference<String> CFG_APPLICATION_NAMESPACE =
            varRef(s_loc, "nop.application.namespace", String.class, "default");

    @Description("应用程序的前端文件时间戳，生成的js文件等前端文件缺省都会带有这个时间戳")
    IConfigReference<Long> CFG_WEB_FILE_TIMESTAMP =
            varRef(s_loc, "nop.web.file-timestamp", Long.class, 0L);

    @Description("RPC调用时自动传播的header的名称，采用逗号分隔，例如nop-svc-route")
    IConfigReference<String> CFG_RPC_PROPAGATE_HEADERS =
            varRef(s_loc, "nop.rpc.propagate-headers", String.class,
                    ApiConstants.HEADER_SVC_ROUTE + "," + ApiConstants.HEADER_TAGS
                            + "," + ApiConstants.HEADER_CLIENT_ADDR);

    @Description("是否启用操作权限验证")
    IConfigReference<Boolean> CFG_AUTH_ENABLE_ACTION_AUTH =
            varRef(s_loc, "nop.auth.enable-action-auth", Boolean.class, false);

    @Description("是否启用数据权限验证")
    IConfigReference<Boolean> CFG_AUTH_ENABLE_DATA_AUTH =
            varRef(s_loc, "nop.auth.enable-data-auth", Boolean.class, false);


//    @Description("时间戳类型是否返回毫秒信息")
//    IConfigReference<Boolean> CFG_CONVERT_IGNORE_MILLIS_IN_TIMESTAMP =
//            varRef(s_loc, "nop.convert.ignore-millis-in-timestamp", Boolean.class, true);

}