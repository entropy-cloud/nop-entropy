/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.dsl.orm;

import io.nop.ai.dsl.orm.consts.GptOrmSqlType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.dialect.SQLDataType;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslKeys;

public class GptOrmModelParser {
    public OrmModel parseOrmModel(XNode node) {
        node.setAttr(XDslKeys.DEFAULT.SCHEMA, GptOrmConstants.XDEF_GPT_ORM);
        node.setAttr(XDslKeys.DEFAULT.VALIDATED, true);
        DynamicObject obj = (DynamicObject) new DslModelParser().ignoreUnknown(true).parseFromNode(node);

        OrmModel model = BeanTool.buildBean(obj, OrmModel.class);
        normalizeOrm(model);
        return model;
    }

    private void normalizeOrm(OrmModel ormModel) {
        for (OrmEntityModel entityModel : ormModel.getEntities()) {
            normalizeEntity(entityModel);
        }
    }

    private void normalizeEntity(OrmEntityModel entityModel) {
        String code = StringHelper.camelCaseToUnderscore(StringHelper.firstPart(entityModel.getName(), '.'), true);
        entityModel.setTableName(code);

        String name = StringHelper.camelCase(code, true);
        entityModel.setName(name);
        if(StringHelper.isEmpty(entityModel.getDisplayName()))
            entityModel.setDisplayName(name);

        entityModel.setClassName("app.demo." + StringHelper.simpleClassName(name));

        int nextId = 1;
        for (OrmColumnModel col : entityModel.getColumns()) {
            normalizeCol(col);
            col.setPropId(nextId++);
        }
    }

    private void normalizeCol(OrmColumnModel col) {
        String code = StringHelper.camelCaseToUnderscore(col.getName(), false);
        String name = StringHelper.camelCase(code, false);
        String sqlType = col.getSqlType();
        if (!StringHelper.isEmpty(sqlType)) {
            sqlType = sqlType.trim().toUpperCase();
            sqlType = StringHelper.replace(sqlType, " AUTO_INCREMENT", "");
            SQLDataType dataType = SQLDataType.parse(sqlType);
            col.setStdSqlType(GptOrmSqlType.getStdSqlType(dataType.getName()));
            if (dataType.isAllowPrecision())
                col.setPrecision(dataType.getPrecision());
            if (dataType.isAllowScale())
                col.setScale(dataType.getScale());
        } else {
            col.setStdSqlType(StdSqlType.VARCHAR);
            col.setPrecision(50);
        }
        col.setName(name);
        col.setCode(code);
    }
}