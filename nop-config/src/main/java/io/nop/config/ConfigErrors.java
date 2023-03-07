/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface ConfigErrors {
    String ARG_CONFIG_NAME = "configName";
    String ARG_RESOURCE_PATH = "resourcePath";

    String ARG_VALUE_TYPE = "valueType";
    String ARG_DEFINE_TYPE = "defineType";

    ErrorCode ERR_CONFIG_INVALID_CONFIG_BEAN_NAME = define("nop.err.config.invalid-config-bean-name",
            "不合法的配置对象名:{configName}", ARG_CONFIG_NAME);

    ErrorCode ERR_CONFIG_MISSING_BOOTSTRAP_FILE = define("nop.err.config.missing-bootstrap-file",
            "缺少配置文件:{resourcePath}", ARG_RESOURCE_PATH);

    ErrorCode ERR_CONFIG_VALUE_NOT_ALLOW_LIST = define("nop.err.config.value-not-allow-list",
            "配置项[{configName}]的值不能是集合类型，只能是普通数据类型或者Map类型", ARG_CONFIG_NAME);

    ErrorCode ERR_CONFIG_MISSING_APPLICATION_NAME = define("nop.err.config.missing-application-name",
            "缺少nop.application.name配置项");

    ErrorCode ERR_CONFIG_VALUE_TYPE_NOT_SAME_AS_DEFINED = define("nop.err.config.value-type-not-same-as-defined",
            "配置值[{configName}]的类型[{valueType}]与定义类型[{defineType}]不一致", ARG_CONFIG_NAME, ARG_VALUE_TYPE,
            ARG_DEFINE_TYPE);

}
