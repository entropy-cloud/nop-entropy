/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.StdSqlType;

import java.util.List;

public interface IColumnModel extends IEntityPropModel {

    String getName();

    String getDomain();

    default String getBaseDomain() {
        return StringHelper.firstPart(getDomain(), '-');
    }

    String getStdDomain();

    String getCode();

    int getPropId();

    boolean isInsertable();

    boolean isUpdatable();

    String getSqlText();

    boolean isMandatory();

    boolean isPrimary();

    StdSqlType getStdSqlType();

    String getDefaultValue();

    Integer getPrecision();

    Integer getScale();

    StdDataType getStdDataType();

    /**
     * 列所对应的to-one引用对象。例如status_id字段关联Status表，对应引用对象status。一般情况下一个字段最多只有唯一一个关联对象
     */
    List<IEntityRelationModel> getColumnRefs();

    default String getJavaTypeName() {
        return getJavaClassName();
    }

    default Class<?> getJavaClass() {
        return getStdDataType().getJavaClass();
    }

    default String getJavaClassName() {
        return getJavaClass().getName();
    }
}