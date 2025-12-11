/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface XlsxErrors {
    String ARG_TYPE = "type";
    String ARG_REL_ID = "relId";
    String ARG_SHEET_NAME = "sheetName";

    ErrorCode ERR_XLSX_NULL_REL_PART = define("nop.err.xlsx.null-rel-part", "没有关联文件:type={type},relId={relId}", ARG_TYPE,
            ARG_REL_ID);

    ErrorCode ERR_XLSX_UNKNOWN_SHEET_NAME = define("nop.err.xlsx.unknown-sheet-name",
            "找不到sheet名称为:{sheetName}", ARG_SHEET_NAME);
}
