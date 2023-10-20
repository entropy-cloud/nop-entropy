/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
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

    String ARG_FILE_OBJ_NAME = "fileObjName";

    ErrorCode ERR_FILE_LENGTH_EXCEED_LIMIT = define("nop.err.file.length-exceed-limit",
            "上传文件长度{}超过限制{maxLength}", ARG_LENGTH, ARG_MAX_LENGTH);

    ErrorCode ERR_FILE_NOT_ALLOW_FILE_EXT = define("nop.err.file.not-allow-file-ext",
            "上传文件的后缀名为[{fileExt}]，不在允许的文件类型范围内。只允许如下文件:{allowedFileExts}",
            ARG_FILE_EXT, ARG_ALLOWED_FILE_EXTS);

    ErrorCode ERR_FILE_NOT_ALLOW_ACCESS_FILE = define("nop.err.file.not-allow-access-file",
            "没有访问文件的权限:{fileId}", ARG_FILE_ID);

    ErrorCode ERR_FILE_NOT_EXISTS = define("nop.err.file.not-exists",
            "文件不存在:{fileId}", ARG_FILE_ID);

    ErrorCode ERR_FILE_INVALID_BIZ_OBJ_NAME = define("nop.err.file.invalid-biz-obj-name",
            "非法的对象名:{bizObjName}", ARG_BIZ_OBJ_NAME);

    ErrorCode ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ = define("nop.err.file.attach-file-not-same-obj",
            "文件与实体绑定时指定的对象名不一致:bizObjName={bizObjName},fileId={fileId},fileObj={fileObjName}",
            ARG_BIZ_OBJ_NAME, ARG_FILE_ID, ARG_FILE_OBJ_NAME);
}
