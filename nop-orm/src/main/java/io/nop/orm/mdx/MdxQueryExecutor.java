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
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.sql.SQL;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_QUERY_TIMEOUT;

public class MdxQueryExecutor {
    static final Logger LOG = LoggerFactory.getLogger(MdxQueryExecutor.class);

    private final IOrmTemplate ormTemplate;

    public MdxQueryExecutor(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    public <T> T findFirst(QueryBean query, IRowMapper<T> resultMapper) {
        query.setLimit(1);
        List<T> ret = findList(query, resultMapper);
        if (ret.isEmpty())
            return null;
        return ret.get(0);
    }


    public boolean exists(QueryBean query) {
        int timeout = query.getTimeout() == null ? 0 : query.getTimeout();
        IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModel(query.getSourceName());

        MdxQuerySplitter splitter = new MdxQuerySplitter();
        List<QueryBean> queries = splitter.split(query, entityModel);

        QueryBean mainQuery = queries.get(0);
        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(mainQuery, entityModel.getDeleteFlagProp()).timeout(timeout)
                .end();
        return ormTemplate.exists(sql);
    }

    private BaseDataSetMeta newDataSetMeta(QueryBean query, Map<String, String> sourceNames) {
        List<BaseDataFieldMeta> fieldMetas = new ArrayList<>(query.getFields().size());
        for (QueryFieldBean field : query.getFields()) {
            String owner = field.getOwner();
            String sourceName = sourceNames.get(owner);
            if (sourceName == null)
                sourceName = query.getSourceName();

            BaseDataFieldMeta fieldMeta = new BaseDataFieldMeta(field.getLabel(), field.getName(), sourceName,
                    StdDataType.ANY,false);
            fieldMetas.add(fieldMeta);
        }
        return new BaseDataSetMeta(fieldMetas);
    }

    public <T> List<T> findList(QueryBean query, IRowMapper<T> resultMapper) {
        int timeout = query.getTimeout() == null ? 0 : query.getTimeout();

        long beginTime = CoreMetrics.currentTimeMillis();

        IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModel(query.getSourceName());

        MdxQuerySplitter splitter = new MdxQuerySplitter();
        List<QueryBean> queries = splitter.split(query, entityModel);

        QueryBean mainQuery = queries.get(0);
        Map<Object, Map<String, Object>> dimIndex = new HashMap<>();

        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(mainQuery, entityModel.getDeleteFlagProp()).timeout(timeout)
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

        // 合并子表查询结果到主表查询结果上，按照dimField进行对齐
        for (int i = 1, n = queries.size(); i < n; i++) {
            beginTime = CoreMetrics.currentTimeMillis();

            addSubQueryResult(dimIndex, queries.get(i), mainQuery.getLimit() > 0, timeout);

            timeout = CoreMetrics.calcNewTimeout(timeout, beginTime,
                    err -> new NopException(ERR_ORM_QUERY_TIMEOUT).param(ARG_NAME, query.getName()));
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

    private IRowMapper<Map<String, Object>> mainMapper(List<QueryBean> queries,
                                                       Map<Object, Map<String, Object>> dimIndex) {
        QueryBean mainQuery = queries.get(0);
        return (row, rowNumber, colMapper) -> {
            Object[] values = row.getFieldValues();
            Map<String, Object> map = new HashMap<>();

            if (queries.size() > 1) {
                // 按照dimFields建立索引。dimFields总对应于结果集的最前面几列
                Object dimValue = buildDimValue(values, mainQuery.getDimFields().size());
                dimIndex.put(dimValue, map);
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

    private void addSubQueryResult(Map<Object, Map<String, Object>> dimIndex, QueryBean query, boolean pageQuery,
                                   int timeout) {
        if (pageQuery) {
            if (query.getDimFields().size() == 1) {
                query.addFilter(FilterBeans.in(query.getDimFields().get(0), dimIndex.keySet()));
            } else {
                List<TreeBean> or = new ArrayList<>(dimIndex.size());
                for (Object dimValue : dimIndex.keySet()) {
                    List<String> dimFields = query.getDimFields();
                    List<TreeBean> and = new ArrayList<>(dimFields.size());
                    List<Object> list = (List<Object>) dimValue;
                    for (int i = 0, n = list.size(); i < n; i++) {
                        and.add(FilterBeans.eq(dimFields.get(i), list.get(i)));
                    }
                    or.add(FilterBeans.and(and));
                }
                query.addFilter(FilterBeans.or(or));
            }
        }

        IEntityModel entityModel = ormTemplate.getOrmModel().requireEntityModel(query.getSourceName());

        SQL sql = DaoQueryHelper.queryToSelectFieldsSql(query, entityModel.getDeleteFlagProp()).timeout(timeout).end();

        IRowMapper<Void> rowMapper = (row, rowNumber, colMapper) -> {
            Object[] values = row.getFieldValues();
            Object dimValue = buildDimValue(values, query.getDimFields().size());
            Map<String, Object> main = dimIndex.get(dimValue);
            if (main != null) {
                for (int i = 0, n = query.getFields().size(); i < n; i++) {
                    QueryFieldBean field = query.getFields().get(i);
                    if (!field.isInternal()) {
                        main.put(field.getLabel(), values[i]);
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

    // 为了避免不同表中关联字段的数据类型不同导致不匹配，强制把关联字段值转换为字符串
    Object buildDimValue(Object[] values, int dimSize) {
        if (dimSize == 1)
            return ConvertHelper.toString(values[0], "");

        List<Object> ret = new ArrayList<>(dimSize);
        for (int i = 0; i < dimSize; i++) {
            ret.add(ConvertHelper.toString(values[i], ""));
        }
        return ret;
    }
}