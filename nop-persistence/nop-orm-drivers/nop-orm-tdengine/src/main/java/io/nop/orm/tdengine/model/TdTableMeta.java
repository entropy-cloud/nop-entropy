/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.tdengine.TdEngineConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static io.nop.orm.tdengine.TdEngineErrors.ARG_PROP_NAME;
import static io.nop.orm.tdengine.TdEngineErrors.ARG_TABLE_NAME;
import static io.nop.orm.tdengine.TdEngineErrors.ERR_TDENGINE_INVALID_SUB_TABLE_NAME;

public class TdTableMeta {
    /**
     * 子表名由业务数据（nbr 列的值）拼接为 SQL 标识符，且 TDengine 不支持对表名做参数绑定，
     * 因此必须做白名单校验，防止脏数据或恶意输入破坏/注入 SQL
     */
    private static final Pattern SUB_TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private final IEntityModel entityModel;

    private final List<IColumnModel> tagCols = new ArrayList<>();

    private final List<IColumnModel> dataCols = new ArrayList<>();

    private final IColumnModel tsCol;

    private final IColumnModel nbrCol;

    private final String superTableName;

    public TdTableMeta(IEntityModel entityModel) {
        this.entityModel = entityModel;
        this.tsCol = entityModel.requireColumnByTag(TdEngineConstants.TAG_TS);
        this.nbrCol = entityModel.requireColumnByTag(TdEngineConstants.TAG_NBR);
        this.superTableName = getSuperTableName(entityModel);

        for (IColumnModel col : entityModel.getColumns()) {
            if (col == nbrCol)
                continue;
            if (col == tsCol) {
                dataCols.add(col);
            } else if (col.containsTag(TdEngineConstants.TAG_TAG)) {
                tagCols.add(col);
            } else {
                dataCols.add(col);
            }
        }
    }

    private static String getSuperTableName(IEntityModel entityModel) {
        String schema = entityModel.getDbSchema();
        if (StringHelper.isEmpty(schema)) {
            schema = "db0";
        }
        return schema + "." + entityModel.getTableName();
    }

    public String getSubTableName(IOrmEntity entity) {
        Object v = entity.orm_propValue(nbrCol.getPropId());
        String name = v instanceof Number ? "dev" + v : StringHelper.toString(v, null);
        if (name == null || !SUB_TABLE_NAME_PATTERN.matcher(name).matches())
            throw new NopException(ERR_TDENGINE_INVALID_SUB_TABLE_NAME)
                    .param(ARG_TABLE_NAME, name).param(ARG_PROP_NAME, nbrCol.getName());
        return name;
    }

    public String getSuperTableName() {
        return superTableName;
    }

    public IEntityModel getEntityModel() {
        return entityModel;
    }

    public List<IColumnModel> getTagCols() {
        return tagCols;
    }

    public List<IColumnModel> getDataCols() {
        return dataCols;
    }

    public IColumnModel getTsCol() {
        return tsCol;
    }

    public IColumnModel getNbrCol() {
        return nbrCol;
    }
}