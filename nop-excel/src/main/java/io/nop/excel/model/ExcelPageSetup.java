/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.excel.ExcelConstants;
import io.nop.excel.model._gen._ExcelPageSetup;

public class ExcelPageSetup extends _ExcelPageSetup {
    public ExcelPageSetup() {

    }

    public String getOrientation() {
        if (Boolean.TRUE.equals(getOrientationHorizontal()))
            return ExcelConstants.ORIENTATION_LANDSCAPE;
        return ExcelConstants.ORIENTATION_PORTRAIT;
    }
}
