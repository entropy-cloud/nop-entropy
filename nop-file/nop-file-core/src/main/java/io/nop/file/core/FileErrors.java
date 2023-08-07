package io.nop.file.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface FileErrors {
    String ARG_LENGTH = "length";
    String ARG_MAX_LENGTH = "maxLength";

    String ARG_FILE_EXT = "fileExt";

    String ARG_ALLOWED_FILE_EXTS = "allowedFileExts";

    String ARG_FILE_ID = "fileId";

    String ARG_BIZ_OBJ_NAME = "bizObjName";

    String ARG_BIZ_OBJ_ID = "bizObjId";

    String ARG_FIELD_NAME = "fieldName";
    ErrorCode ERR_FILE_LENGTH_EXCEED_LIMIT = define("nop.err.file.length-exceed-limit",
            "上传文件长度{}超过限制{maxLength}", ARG_LENGTH, ARG_MAX_LENGTH);

    ErrorCode ERR_FILE_NOT_ALLOW_FILE_EXT = define("nop.err.file.not-allow-file-ext",
            "上传文件的后缀名为[{fileExt}]，不在允许的文件类型范围内。只允许如下文件:{allowedFileExts}",
            ARG_FILE_EXT, ARG_ALLOWED_FILE_EXTS);

    ErrorCode ERR_FILE_NOT_ALLOW_ACCESS_FILE = define("nop.err.file.not-allow-access-file",
            "没有访问文件的权限:{}", ARG_FILE_ID);
}
