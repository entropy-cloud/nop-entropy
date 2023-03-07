/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;

/**
 * 类似mybatis，通过sq-lib.xml文件来统一管理系统中的SQL语句。 sql-lib.xml支持EQL和SQL两种语法，支持缓存、超时时间等设置。
 */
public interface ISqlLibManager {

    SqlItemModel getSqlItemModel(String sqlName);

    SQL buildSql(String sqlName, IEvalContext context);

    Object invoke(String sqlName, LongRangeBean range, IEvalContext context);

    /**
     * 类似MyBatis为sql映射文件提供一个强类型的调用代理类
     */
    <T> T createProxy(Class<T> proxyClass);
}
