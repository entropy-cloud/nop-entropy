/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.excel.model._gen._ExcelPageBreaks;

/**
 * 手动分页符（WI3）：rows/cols 为 0-based 行/列索引，break 发生在该行/列之后——
 * 即该行/列是分页后前一个页面的最后一个行/列（对齐 OOXML brk@id 语义）。
 */
public class ExcelPageBreaks extends _ExcelPageBreaks {
    public ExcelPageBreaks() {

    }
}
