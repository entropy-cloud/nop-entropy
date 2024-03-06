/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.loader;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.sql.SQL;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSet;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.session.IOrmSessionImplementor;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.Function;

public interface IQueryExecutor {

    long executeUpdate(@Nonnull IOrmSessionImplementor session, @Nonnull SQL sql);

    <T> T executeQuery(@Nonnull IOrmSessionImplementor session, @Nonnull SQL sql, LongRangeBean range,
                       @Nonnull Function<? super IDataSet, T> callback);

    <T> T executeStatement(@Nonnull IOrmSessionImplementor session, @Nonnull SQL sql, LongRangeBean range,
                           @Nonnull Function<IComplexDataSet, T> callback, ICancelToken cancelToken);

    long executeUpdateSql(@Nonnull IOrmSessionImplementor session, ICompiledSql compiled, List<Object> markerValues);

    <T> T executeQuerySql(@Nonnull IOrmSessionImplementor session, @Nonnull ICompiledSql compiled,
                          @Nonnull List<Object> markerValues, LongRangeBean range,
                          @Nonnull Function<? super IDataSet, T> callback);

    <T> T executeStatementSql(@Nonnull IOrmSessionImplementor session, @Nonnull ICompiledSql compiled,
                              @Nonnull List<Object> markerValues, LongRangeBean range,
                              @Nonnull Function<IComplexDataSet, T> callback, ICancelToken cancelToken);

}