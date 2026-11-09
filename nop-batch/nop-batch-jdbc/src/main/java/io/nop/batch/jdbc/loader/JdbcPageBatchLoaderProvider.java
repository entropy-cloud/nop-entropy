/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nop.core.lang.sql.ISqlGenerator;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.INamedSqlBuilder;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import jakarta.inject.Inject;

import java.util.List;

import static io.nop.batch.core.BatchErrors.ARG_BATCH_SIZE;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_BATCH_SIZE_INVALID;

public class JdbcPageBatchLoaderProvider<T> implements IBatchLoaderProvider<T> {
    static final Logger LOG = LoggerFactory.getLogger(JdbcPageBatchLoaderProvider.class);
    private IJdbcTemplate jdbcTemplate;

    private ISqlGenerator sqlGenerator;

    private INamedSqlBuilder namedSqlBuilder;

    @SuppressWarnings("unchecked")
    private IRowMapper<T> rowMapper = (IRowMapper<T>) ColumnMapRowMapper.CASE_INSENSITIVE;


    public void setRowMapper(IRowMapper<T> rowMapper) {
        this.rowMapper = rowMapper;
    }

    @Inject
    public void setNamedSqlBuilder(INamedSqlBuilder sqlBuilder) {
        this.namedSqlBuilder = sqlBuilder;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setSqlGenerator(ISqlGenerator sqlGenerator) {
        Guard.notNull(sqlGenerator, "sqlGenerator");
        Guard.checkState(this.sqlGenerator == null, "sqlGenerator not allow change");
        this.sqlGenerator = sqlGenerator;
    }

    public void setSqlText(String sql) {
        setSql(SQL.begin().sql(sql).end());
    }

    public void setSql(SQL sql) {
        setSqlGenerator(ctx -> sql);
    }

    public void setSqlName(String sqlName) {
        setSqlGenerator(ctx -> namedSqlBuilder.buildSql(sqlName, ctx));
    }

    static class LoaderState {
        SQL sql;
        LongRangeBean range;
        /** 1.7-C7-008（plan 2546 W-04）：一次性 offset 语义告警（放 LoaderState，避免多任务串扰）。 */
        boolean offsetWarned;
    }

    @Override
    public IBatchLoader<T> setup(IBatchTaskContext context) {
        LoaderState state = new LoaderState();
        state.sql = sqlGenerator.generateSql(context);

        return (batchSize, ctx) -> {
            synchronized (state) {
                return load(batchSize, ctx, state);
            }
        };
    }

    List<T> load(int batchSize, IBatchChunkContext context, LoaderState state) {
        // 1.7-C7-008（plan 2546 W-04）：batchSize 守卫落在 load 侧——BatchChunkProcessor.adjustBatchSize
        // 的钳位发生在 load 之后（process→adjustBatchSize→loadItems→load），此处是唯一能在钳位前拦截的点。
        if (batchSize <= 0)
            throw new NopException(ERR_BATCH_BATCH_SIZE_INVALID).param(ARG_BATCH_SIZE, batchSize);

        if (!state.offsetWarned) {
            state.offsetWarned = true;
            // 1.7-C7-008（plan 2546 W-04）：页模式以 offset 翻页——每页扫描代价随进度线性增长
            //（全任务 O(N²/2B)），且并发删除源表行会导致区间前移跳行。适用于静态快照表；
            // 活跃表应改用 orm-reader（keyset）或 streaming（JdbcBatchLoaderProvider）。
            LOG.warn("nop.warn.batch.jdbc-page-offset-paging:sql={}", state.sql.getText());
        }

        LongRangeBean range = state.range;
        if (range == null) {
            range = LongRangeBean.longRange(0, batchSize);
        } else {
            range = LongRangeBean.longRange(range.getEnd(), batchSize);
        }

        List<T> page = jdbcTemplate.findPage(state.sql, range.getOffset(), (int) range.getLimit(), rowMapper);
        // 分页查询成功后才提交游标。若查询前就推进state.range，查询瞬时失败后
        // 上层重试（如RetryBatchLoader）会从下一页开始，失败页被静默跳过造成数据丢失
        state.range = range;
        return page;
    }
}
