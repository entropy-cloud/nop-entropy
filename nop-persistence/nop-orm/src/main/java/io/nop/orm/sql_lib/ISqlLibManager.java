/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.INamedSqlBuilder;

import java.util.Map;

/**
 * 类似mybatis，通过sq-lib.xml文件来统一管理系统中的SQL语句。 sql-lib.xml支持EQL和SQL两种语法，支持缓存、超时时间等设置。
 */
public interface ISqlLibManager extends INamedSqlBuilder {

    SqlItemModel getSqlItemModel(String sqlName);

    QueryBean buildQueryBean(String sqlName, IEvalContext context);

    SQL buildSql(String sqlName, IEvalContext context);

    Object invoke(String sqlName, LongRangeBean range, IEvalContext context);

    default Object invoke(String sqlName, LongRangeBean range, Map<String, Object> args, IEvalContext context) {
        IEvalScope scope = context.getEvalScope().newChildScope();
        if (args != null) {
            scope.setLocalValues(args);
        }
        return invoke(sqlName, range, scope);
    }

    /**
     * 自动执行SQL语句的validate-input段来获取测试输入，验证SQL语句解析正确
     *
     * @param sqlLibPath SQL语句库对应的文件路径
     */
    void checkLibValid(String sqlLibPath);

    /**
     * 类似MyBatis为sql映射文件提供一个强类型的调用代理类
     */
    <T> T createProxy(Class<T> proxyClass);
}
