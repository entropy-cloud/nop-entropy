/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.mdx;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.collections.IntArray;
import io.nop.commons.type.StdDataType;
import io.nop.core.CoreConfigs;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.impl.BaseDataFieldMeta;
import io.nop.dataset.impl.BaseDataSetMeta;
import io.nop.dataset.impl.DefaultFieldMapper;
import io.nop.dataset.impl.MapDataRow;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.model.IEntityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.util.CollectionHelper.toNotNull;
import static io.nop.orm.OrmErrors.ARG_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_TIMEOUT;

public class MdxQueryExecutor {
    static final Logger LOG = LoggerFactory.getLogger(MdxQueryExecutor.class);

    private final IOrmTemplate ormTemplate;

    /** Plan 2309 D1：ORDER BY 空序门控需要方言（见 DaoQueryHelper.appendOrderBy） */
    private IDialect dialectOf(IEntityModel entityModel) {
        return ormTemplate.getDialectForQuerySpace(entityModel.getQuerySpace());
    }

    public MdxQueryExecutor(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    public <T> T findFirst(QueryBean query, IRowMapper<T> resultMapper) {
        // 复制后再改 limit，否则调用方持有的 QueryBean 会被永久改成 limit=1
        QueryBean cloned = query.cloneInstance();
        cloned.setLimit(1);
        List<T> ret = findList(cloned, resultMapper);
        if (ret.isEmpty())
            return null;
        return ret.get(0);
    }

    public boolean exists(QueryBean query) {
        int timeout = query.getTimeout() == null ? 0 : query.getTimeout();
        IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModel(query.getSourceName());

        MdxQuerySplitter splitter = new MdxQuerySplitter();
        List<MdxQueryBean> queries = splitter.split(query, entityModel);

        QueryBean mainQuery = queries.get(0);
        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(dialectOf(entityModel), mainQuery, entityModel.getDeleteFlagProp()).timeout(timeout)
                .end();
        return ormTemplate.exists(sql);
    }

    private BaseDataSetMeta newDataSetMeta(QueryBean query, Map<String, String> sourceNames) {
        List<QueryFieldBean> fields = toNotNull(query.getFields());
        List<BaseDataFieldMeta> fieldMetas = new ArrayList<>(fields.size());
        for (QueryFieldBean field : fields) {
            String owner = field.getOwner();
            String sourceName = sourceNames.get(owner);
            if (sourceName == null)
                sourceName = query.getSourceName();

            BaseDataFieldMeta fieldMeta = new BaseDataFieldMeta(field.getLabel(), field.getName(), sourceName,
                    StdDataType.ANY, null, false);
            fieldMetas.add(fieldMeta);
        }
        return new BaseDataSetMeta(fieldMetas);
    }

    public <T> List<T> findList(QueryBean query, IRowMapper<T> resultMapper) {
        int timeout = query.getTimeout() == null ? 0 : query.getTimeout();

        long beginTime = CoreMetrics.currentTimeMillis();

        IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModel(query.getSourceName());

        MdxQuerySplitter splitter = new MdxQuerySplitter();
        List<MdxQueryBean> queries = splitter.split(query, entityModel);

        QueryBean mainQuery = queries.get(0);
        Map<Object, List<Map<String, Object>>> dimIndex = new HashMap<>();

        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(dialectOf(entityModel), mainQuery, entityModel.getDeleteFlagProp()).timeout(timeout)
                .end();
        IRowMapper<Map<String, Object>> rowMapper = mainMapper(queries, dimIndex);

        List<Map<String, Object>> ret;

        // 先获取主表数据
        if (query.getLimit() > 0) {
            // 分页查询
            ret = ormTemplate.findPage(sql, query.getOffset(), query.getLimit(), rowMapper);
        } else {
            // 读取全部数据
            ret = ormTemplate.findAll(sql, rowMapper);
        }

        timeout = CoreMetrics.calcNewTimeout(timeout, beginTime,
                err -> new NopException(ERR_ORM_QUERY_TIMEOUT).param(ARG_NAME, query.getName()));

        if (!dimIndex.isEmpty()) {
            boolean filterSub = mainQuery.getLimit() > 0 || mainQuery.getFilter() != null;

            // 合并子表查询结果到主表查询结果上，按照dimField进行对齐
            for (int i = 1, n = queries.size(); i < n; i++) {
                beginTime = CoreMetrics.currentTimeMillis();

                addSubQueryResult(dimIndex, queries.get(i), filterSub, timeout);

                timeout = CoreMetrics.calcNewTimeout(timeout, beginTime,
                        err -> new NopException(ERR_ORM_QUERY_TIMEOUT).param(ARG_NAME, query.getName()));
            }
        }

        BaseDataSetMeta meta = newDataSetMeta(query, splitter.getSourceNames());
        List<T> records = new ArrayList<>(ret.size());
        for (int i = 0, n = ret.size(); i < n; i++) {
            IDataRow row = new MapDataRow(meta, true, ret.get(i));
            T record = resultMapper.mapRow(row, i + 1L, DefaultFieldMapper.INSTANCE);
            records.add(record);
        }
        return records;
    }

    private IRowMapper<Map<String, Object>> mainMapper(List<MdxQueryBean> queries,
                                                       Map<Object, List<Map<String, Object>>> dimIndex) {
        MdxQueryBean mainQuery = queries.get(0);
        return (row, rowNumber, colMapper) -> {
            Object[] values = row.getFieldValues();
            Map<String, Object> map = new HashMap<>();

            if (queries.size() > 1) {
                // 按照dimFields建立索引。dimFields总对应于结果集的最前面几列。
                // dimFields不保证唯一（主表无聚合时不做GROUP BY），同一维度值可能对应多行，
                // 子表数据必须回填到所有匹配行，否则其余行的子字段会静默为空。
                Object dimValue = buildDimValue(values, mainQuery.getDimFieldIndexes());
                dimIndex.computeIfAbsent(dimValue, k -> new ArrayList<>(1)).add(map);
            }

            for (int i = 0, n = mainQuery.getFields().size(); i < n; i++) {
                QueryFieldBean field = mainQuery.getFields().get(i);
                if (!field.isInternal()) {
                    map.put(field.getLabel(), values[i]);
                }
            }

            return map;
        };
    }

    private void addSubQueryResult(Map<Object, List<Map<String, Object>>> dimIndex, MdxQueryBean query,
                                   boolean filterSub, int timeout) {
        if (filterSub) {
            query.addFilter(buildDimFilter(query.getDimFields(), dimIndex.keySet()));
        }

        IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModel(query.getSourceName());

        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(dialectOf(entityModel), query, entityModel.getDeleteFlagProp()).timeout(timeout).end();

        IRowMapper<Void> rowMapper = (row, rowNumber, colMapper) -> {
            Object[] values = row.getFieldValues();
            Object dimValue = buildDimValue(values, query.getDimFieldIndexes());
            List<Map<String, Object>> targets = dimIndex.get(dimValue);
            if (targets != null) {
                for (int i = 0, n = query.getFields().size(); i < n; i++) {
                    QueryFieldBean field = query.getFields().get(i);
                    if (!field.isInternal()) {
                        for (Map<String, Object> target : targets) {
                            target.put(field.getLabel(), values[i]);
                        }
                    }
                }
            } else {
                // 不应该执行到这里
                LOG.error("nop.orm.mdx-sub-query-row-not-match:dimValue={}", dimValue);
            }
            return null;
        };

        ormTemplate.findAll(sql, rowMapper);
    }

    /**
     * 构造子查询的维度过滤条件。
     *
     * <p>该过滤<b>只用于缩小子表扫描范围</b>，不参与正确性判断——真正决定能否合并的是
     * {@code dimIndex.get(dimValue)} 的精确匹配。因此宁可生成偏宽的SQL，也不允许生成偏窄的SQL。
     *
     * <p>两个必须处理的细节：
     * <ul>
     *     <li><b>NULL 不能当作空字符串</b>。维度值来自 {@link #buildDimValue}，NULL 保留为 {@code null}。
     *     早期实现把它转成 {@code ""} 再塞进 IN 列表，数值列会直接报错（{@code invalid input syntax}），
     *     字符列则永远匹配不到 NULL；而复合维度的 {@code eq(col, "")} 又会被改写成 {@code is null}，
     *     同一个语义在两条分支上行为相反。</li>
     *     <li><b>IN 列表有硬上限</b>。{@link CoreConfigs#CFG_CORE_SQL_IN_LIST_MAX_SIZE} 默认为 1000，
     *     超过即抛 {@code nop.err.sql.in-list-too-large}。主表行数可以远大于这个值，必须分片后 OR 合并。</li>
     * </ul>
     */
    TreeBean buildDimFilter(List<String> dimFields, Collection<Object> dimValues) {
        int maxChunk = CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE.get();
        List<TreeBean> or = new ArrayList<>(dimValues.size());

        if (dimFields.size() == 1) {
            String dimField = dimFields.get(0);
            List<Object> nonNullValues = new ArrayList<>(dimValues.size());
            boolean hasNull = false;
            for (Object dimValue : dimValues) {
                if (dimValue == null)
                    hasNull = true;
                else
                    nonNullValues.add(dimValue);
            }
            for (List<Object> chunk : chunk(nonNullValues, maxChunk))
                or.add(FilterBeans.in(dimField, chunk));
            if (hasNull)
                or.add(FilterBeans.isNull(dimField));
        } else {
            for (Object dimValue : dimValues) {
                List<TreeBean> and = new ArrayList<>(dimFields.size());
                List<Object> parts = (List<Object>) dimValue;
                for (int i = 0, n = dimFields.size(); i < n; i++) {
                    Object part = parts.get(i);
                    and.add(part == null ? FilterBeans.isNull(dimFields.get(i))
                            : FilterBeans.eq(dimFields.get(i), part));
                }
                or.add(FilterBeans.and(and));
            }
        }

        return FilterBeans.or(or);
    }

    private static List<List<Object>> chunk(List<Object> values, int size) {
        List<List<Object>> ret = new ArrayList<>((values.size() + size - 1) / size);
        for (int i = 0; i < values.size(); i += size) {
            ret.add(values.subList(i, Math.min(i + size, values.size())));
        }
        return ret;
    }

    /**
     * 构造维度对齐用的键。为了避免不同表中关联字段的数据类型不同导致不匹配，
     * 强制把关联字段值转换为字符串。
     *
     * <p>SQL NULL 保留为 {@code null} 而不转成空字符串，否则 NULL 会与真正的空字符串混为一谈，
     * 且反写进过滤条件时会产生类型非法的字面量。
     */
    Object buildDimValue(Object[] values, IntArray dimIndexes) {
        int dimSize = dimIndexes.size();
        if (dimSize == 1) {
            Object value = values[dimIndexes.get(0)];
            return value == null ? null : ConvertHelper.toString(value, "");
        }

        List<Object> ret = new ArrayList<>(dimSize);
        for (int i = 0; i < dimSize; i++) {
            Object value = values[dimIndexes.get(i)];
            ret.add(value == null ? null : ConvertHelper.toString(value, ""));
        }
        return ret;
    }
}