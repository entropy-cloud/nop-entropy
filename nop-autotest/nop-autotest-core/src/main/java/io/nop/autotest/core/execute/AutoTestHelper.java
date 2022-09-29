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
