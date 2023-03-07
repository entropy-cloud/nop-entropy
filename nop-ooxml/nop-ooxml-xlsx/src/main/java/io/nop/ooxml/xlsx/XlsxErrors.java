/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface XlsxErrors {
    String ARG_TYPE = "type";
    String ARG_REL_ID = "relId";

    ErrorCode ERR_XLSX_NULL_REL_PART = define("nop.err.xlsx.null-rel-part", "没有关联文件:type={},relId={}", ARG_TYPE,
            ARG_REL_ID);
}
