/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli;

import io.nop.api.core.exceptions.ErrorCode;

public interface CliErrors {
    String ARG_NAME = "name";
    String ARG_PATH = "path";

    ErrorCode ERR_CLI_UNKNOWN_SCRIPT =
            ErrorCode.define("nop.err.cli.unknown-script",
                    "未找到脚本文件:{path}", ARG_NAME, ARG_PATH);

    ErrorCode ERR_CLI_UNREGISTERED_COMPONENT_MODEL =
            ErrorCode.define("nop.err.cli.unregistered-component-model",
                    "模型文件的格式没有在组件管理器中注册，无法解析:{path}", ARG_PATH);

    ErrorCode ERR_CLI_MODEL_OBJECT_NO_XDSL_SCHEMA =
            ErrorCode.define("nop.err.cli.model-object-no-xdsl-schema",
                    "模型对象没有设置x:schema属性，不支持转换为XML格式");
}
