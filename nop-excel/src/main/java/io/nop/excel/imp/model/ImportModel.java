/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.imp.model;

import io.nop.api.core.util.INeedInit;
import io.nop.excel.imp.model._gen._ImportModel;

public class ImportModel extends _ImportModel implements INeedInit {
    public ImportModel() {

    }

    @Override
    public void init() {
        getSheets().forEach(ImportSheetModel::init);

        if (isDefaultStripText()) {
            for (ImportSheetModel sheet : getSheets()) {
                sheet.initStripText(true);
            }
        }
    }
}
