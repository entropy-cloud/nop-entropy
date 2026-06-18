package io.nop.task.ext;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface TaskExtErrors {
    String ARG_DECORATOR_NAME = "decoratorName";

    String ARG_ATTR_NAME = "attrName";

    String ARG_ATTR_VALUE = "attrValue";

    String ARG_REASON = "reason";

    ErrorCode ERR_TASK_DECORATOR_INVALID_CONFIG = define("nop.err.task.ext.decorator-invalid-config",
            "Task step decorator[{decoratorName}]收到无效配置: attr[{attrName}]={attrValue}, reason={reason}",
            ARG_DECORATOR_NAME, ARG_ATTR_NAME, ARG_ATTR_VALUE, ARG_REASON);
}
