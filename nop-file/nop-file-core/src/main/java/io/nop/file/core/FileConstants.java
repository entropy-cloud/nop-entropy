/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.file.core;

public interface FileConstants {
    String PATH_UPLOAD = "/f/upload";

    String PATH_DOWNLOAD = "/f/download";

    String PARAM_BIZ_OBJ_NAME = "bizObjName";
    String PARAM_FIELD_NAME = "fieldName";

    String BUCKET_PREFIX = "bkt_";

    String TEMP_BIZ_OBJ_ID = "__TEMP__"; // 与BizConstants中的TEMP_BIZ_OBJ_ID保持一致

    String MEDIA_TYPE_CONFIG_PATH = "/nop/file/media-type.json";

    String OPERATION_FILE_STORE_UPLOAD = "NopFileStore__upload";

    String OPERATION_FILE_STORE_DOWNLOAD = "NopFileStore__download";
}
