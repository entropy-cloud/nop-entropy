/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.binder.IDataParameterBinder;
import io.nop.dao.dataset.IDataSetMeta;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.model.IEntityModel;

import java.util.List;

/**
 * 根据eql语句编译得到的SQL语句。
 */
public interface ICompiledSql {
    String getName();

    /**
     * 一条SQL语句只能是在一个数据源中执行。目前不支持一条SQL语句访问多个数据源。
     */
    String getQuerySpace();

    SqlStatementKind getStatementKind();

    /**
     * 分析eql语句得到的数据集元数据
     */
    IDataSetMeta getDataSetMeta();

    /**
     * eql语句中涉及到的所有实体对象
     */
    List<IEntityModel> getReadEntityModels();

    /**
     * 对应update/delete/insert语句，返回被修改的实体对象。如果是查询语句，则返回null
     */
    IEntityModel getWriteEntityModel();

    List<IDataParameterBinder> getColumnBinders();

    /**
     * 从底层的JDBC数据集中读取数据得到getDataSetMeta()中描述的列。 列表的长度应该与getDataSetMeta().getColumnCount()相同。
     */
    List<ISqlExprMeta> getFieldMetas();

    /**
     * 包含翻译后的SQL文本以及marker标记。
     */
    SQL getSql();

    /**
     * 根据EQL语句的参数转化得到SQL语句的参数
     */
    List<Object> buildParams(List<Object> input);
}
