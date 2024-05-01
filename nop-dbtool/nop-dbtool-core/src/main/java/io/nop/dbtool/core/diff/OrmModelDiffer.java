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
import io.nop.dao.dialect.IDialect;
import io.nop.dbtool.core.DataBaseMeta;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmUniqueKeyModel;

public class OrmModelDiffer {

    /**
     * 注：需确保两个模型的表名、字段名、约束名、索引名的大小写是一致的，
     * 否则，通过 {@link #diffTables(IDialect, OrmModel, OrmModel)} 进行名字大小写的自动适配
     */
    public IDiffValue diffTables(OrmModel modelA, OrmModel modelB) {
        return diffTables(null, modelA, modelB);
    }

    /**
     * @param dialect
     *         用于自动适配表名、字段名、约束名、索引名的大小写适配，
     *         若其为 null，则不做名字适配处理，需在对比之前自行处理两个模型的名字的大小写差异
     */
    public IDiffValue diffTables(IDialect dialect, OrmModel modelA, OrmModel modelB) {
        BeanDiffOptions options = new BeanDiffOptions();
        options.setIncludeSame(false);
        if (dialect != null) {
            options.setPropValueConverter((obj, prop, value) -> convertPropValue(dialect, obj, prop, value));
        }

        FieldSelectionBean entitySelection = FieldSelectionBean.fromProp(
                "tableName", "displayName", "comment"
        );
        entitySelection.setKeyProp("tableName");

        // <<<<<<<<<<<<< 字段
        FieldSelectionBean colSelection = FieldSelectionBean.fromProp(
                "code", "name", "displayName", "stdSqlType", "stdDataType",
                "precision", "scale", "defaultValue", "mandatory", "comment"
        );
        colSelection.setKeyProp("code");

        entitySelection.addField("columns", colSelection);
        // >>>>>>>>>>>>>>>>

        // >>>>>>>>> 唯一键
        FieldSelectionBean ukSelection = FieldSelectionBean.fromProp(
                "name", "columns", "constraint", "comment"
        );
        // Note：在数据中反向获取的模型中，只有约束名才是可获取的唯一标识
        ukSelection.setKeyProp("constraint");

        entitySelection.addField("uniqueKeys", ukSelection);
        // <<<<<<<<<<

        FieldSelectionBean selection = new FieldSelectionBean();
        selection.addField("entities", entitySelection);
        options.setSelection(selection);

        return new BeanDiffer().beanDiff(modelA, modelB, options);
    }

    private Object convertPropValue(IDialect dialect, Object obj, String prop, Object value) {
        if (value == null) {
            return null;
        }

        // 表名
        if (obj instanceof IEntityModel && "tableName".equals(prop)) {
            return DataBaseMeta.normalizeTableName(dialect, value.toString());
        }
        // 字段名、唯一键约束名
        else if ((obj instanceof IColumnModel && "code".equals(prop)) //
                 || (obj instanceof OrmUniqueKeyModel && "constraint".equals(prop))) {
            return DataBaseMeta.normalizeColName(dialect, value.toString());
        }
        return value;
    }
}
