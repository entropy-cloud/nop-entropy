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
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.impl.BaseDataRow;
import io.nop.dataset.impl.SingleColumnRow;
import io.nop.dataset.impl.TransformedComplexDataSet;
import io.nop.dataset.impl.TransformedDataSet;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmDaoListener;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.sql.GenSqlTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

import static io.nop.orm.persister.OrmAssembly.readRow;

/**
 * @author canonical_entropy@163.com
 */
public class JdbcQueryExecutor implements IQueryExecutor {
    static final Logger LOG = LoggerFactory.getLogger(JdbcQueryExecutor.class);

    private final IPersistEnv env;

    public JdbcQueryExecutor(IPersistEnv env) {
        this.env = env;
    }

    IJdbcTemplate jdbc() {
        return env.jdbc();
    }

    @Override
    public long executeUpdate(IOrmSessionImplementor session, SQL eql) {
        ICompiledSql compiled = env.compileSql(eql.getName(), eql.getText(), eql.isDisableLogicalDelete());
        invokeListener(compiled);

        SQL sql = transformEQL(eql, compiled);
        return jdbc().executeUpdate(sql);
    }

    @Override
    public <T> T executeQuery(IOrmSessionImplementor session, @Nonnull SQL eql, LongRangeBean range,
                              @Nonnull Function<? super IDataSet, T> callback) {
        if (LOG.isDebugEnabled())
            eql.dump("session.executeQuery");
        ICompiledSql compiled = env.compileSql(eql.getName(), eql.getText(), eql.isDisableLogicalDelete());
        invokeListener(compiled);

        SQL sql = transformEQL(eql, compiled);

        return jdbc().executeQuery(sql, range, ds -> {
            ds = new TransformedDataSet(ds, compiled.getDataSetMeta(), rs -> transformRow(rs, compiled, session));
            return callback.apply(ds);
        });
    }

    @Override
    public <T> T executeStatement(IOrmSessionImplementor session, @Nonnull SQL eql, LongRangeBean range,
                                  @Nonnull Function<IComplexDataSet, T> callback, ICancelToken cancelToken) {
        if (LOG.isDebugEnabled())
            eql.dump("session.executeStatement");
        ICompiledSql compiled = env.compileSql(eql.getName(), eql.getText(), eql.isDisableLogicalDelete());
        invokeListener(compiled);
        SQL sql = transformEQL(eql, compiled);
        return jdbc().executeStatement(sql, range, ds -> {
            ds = new TransformedComplexDataSet(ds, compiled.getDataSetMeta(),
                    rs -> transformRow(rs, compiled, session));
            return callback.apply(ds);
        }, cancelToken);
    }

    void invokeListener(ICompiledSql sql) {
        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null) {
            for (IEntityModel entityModel : sql.getReadEntityModels()) {
                daoListener.onRead(entityModel);
            }

            IEntityModel entityModel = sql.getWriteEntityModel();
            if (entityModel != null) {
                switch (sql.getStatementKind()) {
                    case DELETE: {
                        daoListener.onDelete(entityModel);
                        break;
                    }
                    case UPDATE: {
                        daoListener.onUpdate(entityModel);
                        break;
                    }
                    case INSERT: {
                        daoListener.onSave(entityModel);
                        break;
                    }
                }
            }
        }
    }

    private SQL transformEQL(SQL sql, ICompiledSql compiled) {
        List<Object> params = compiled.buildParams(sql.getMarkerValues());
        return new GenSqlTransformer(env.getShardSelector(), env.getOrmModel(), env)
                .transform(compiled.getSql(), params).end();
    }

    protected IDataRow transformRow(IDataRow rs, ICompiledSql query, IOrmSessionImplementor session) {
        Object[] row = readRow(rs, query.getColumnBinders());
        List<ISqlExprMeta> fields = query.getFieldMetas();
        if (fields.size() == 1) {
            ISqlExprMeta field = fields.get(0);
            Object value = field.buildValue(row, 0, session);
            return new SingleColumnRow(query.getDataSetMeta(), value);
        } else {
            int index = 0;
            Object[] values = new Object[fields.size()];
            for (int i = 0, n = fields.size(); i < n; i++) {
                ISqlExprMeta field = fields.get(i);
                values[i] = field.buildValue(row, index, session);
                index += field.getColumnCount();
            }
            return new BaseDataRow(query.getDataSetMeta(), true, values);
        }
    }
}