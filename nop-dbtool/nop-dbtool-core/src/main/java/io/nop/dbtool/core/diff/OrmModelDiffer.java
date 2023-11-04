package io.nop.dbtool.core.diff;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.diff.IDiffValue;
import io.nop.core.reflect.bean.BeanDiffOptions;
import io.nop.core.reflect.bean.BeanDiffer;
import io.nop.orm.model.OrmModel;

public class OrmModelDiffer {

    public IDiffValue diffTables(OrmModel modelA, OrmModel modelB) {
        BeanDiffOptions options = new BeanDiffOptions();
        FieldSelectionBean selection = new FieldSelectionBean();
        FieldSelectionBean entitySelection = FieldSelectionBean.fromProp(
                "tableName", "comment");
        FieldSelectionBean colSelection = FieldSelectionBean.fromProp(
                "stdSqlType", "precision", "scale", "defaultValue", "mandatory", "comment");
        entitySelection.addField("columns", colSelection);
        selection.addField("entities", entitySelection);
        options.setSelection(selection);
        options.setIncludeSame(false);
        return new BeanDiffer().beanDiff(modelA, modelB, options);
    }
}
