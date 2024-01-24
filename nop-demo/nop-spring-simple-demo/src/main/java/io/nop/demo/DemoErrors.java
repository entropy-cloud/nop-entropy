package io.nop.demo;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface DemoErrors {
    String ARG_NAME = "name";

    ErrorCode ERR_DEMO_NOT_FOUND =
            define("nop.err.demo.not-found", "指定数据不存在: {name}", ARG_NAME);
}
