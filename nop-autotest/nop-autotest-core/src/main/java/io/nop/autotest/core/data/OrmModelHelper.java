package io.nop.autotest.core.data;

import io.nop.dao.DaoConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import java.util.ArrayList;
import java.util.List;

public class OrmModelHelper {
    public static List<String> getColNames(IEntityModel entityModel) {
        List<String> ret = new ArrayList<>(entityModel.getColumns().size());
        for (IColumnModel col : entityModel.getColumns()) {
            ret.add(col.getCode());
        }
        return ret;
    }

    public static List<String> getChgFileHeaders(IEntityModel entityModel) {
        List<String> headers = new ArrayList<>();
        headers.add(DaoConstants.PROP_CHANGE_TYPE);
        headers.addAll(getColNames(entityModel));
        return headers;
    }
}
