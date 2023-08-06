package io.nop.file.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface FileErrors {
    String ARG_LENGTH = "length";
    String ARG_MAX_LENGTH = "maxLength";
    ErrorCode ERR_FILE_LENGTH_EXCEED_LIMIT = define("nop.err.file.length-exceed-limit",
            "上传文件长度{}超过限制{maxLength}", ARG_LENGTH, ARG_MAX_LENGTH);
}
