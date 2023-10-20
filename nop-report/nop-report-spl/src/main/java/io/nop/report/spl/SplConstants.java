/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.spl;

import io.nop.commons.util.CollectionHelper;

import java.util.List;

public interface SplConstants {
    String MODEL_TYPE_SPL = "spl";

    String FILE_TYPE_SPL_XLSX = "spl.xlsx";

    String FILE_TYPE_SPLX = "splx";

    String FILE_TYPE_SPL = "spl";

    List<String> FILE_TYPES_SPL_MODEL = CollectionHelper.buildImmutableList(FILE_TYPE_SPL, FILE_TYPE_SPLX);
}
