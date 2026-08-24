/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.execute;

import io.nop.autotest.core.AutoTestConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

public class AutoTestHelper {
    public static boolean isVarCol(IColumnModel colModel) {
        if (colModel.containsTag(AutoTestConstants.TAG_SEQ) || colModel.containsTag(AutoTestConstants.TAG_VAR))
            return true;

        if (colModel.containsTag(AutoTestConstants.TAG_CLOCK))
            return true;

        int propId = colModel.getPropId();
        IEntityModel entityModel = colModel.getOwnerEntityModel();

        if (propId == entityModel.getUpdateTimePropId())
            return true;

        if (propId == entityModel.getCreateTimePropId())
            return true;

        return false;
    }

    public static String getVarNamePrefix(IColumnModel col) {
        return col.getOwnerEntityModel().getShortName() + '@' + col.getName();
    }
}
