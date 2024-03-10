/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
        entitySelection.setKeyProp("tableName");

        FieldSelectionBean colSelection = FieldSelectionBean.fromProp(
                "code","stdSqlType", "precision", "scale", "defaultValue", "mandatory", "comment");
        colSelection.setKeyProp("code");

        entitySelection.addField("columns", colSelection);
        selection.addField("entities", entitySelection);
        options.setSelection(selection);
        options.setIncludeSame(false);
        return new BeanDiffer().beanDiff(modelA, modelB, options);
    }
}
