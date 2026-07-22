/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.datasource;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.List;

/**
 * querySpace→{@link NopMetaDataSource} 解析共享组件（架构基线 §4.4 D2）：按 {@code NopMetaDataSource.querySpace == 目标 querySpace}
 * 查询，**runtime 多匹配显式失败**（AR-03 兜底：ORM 层已有 {@code UK_NOP_META_DS_QUERY_SPACE}
 * 唯一约束，但历史数据可能违反；本 resolver 在 runtime 多匹配时拒绝取首条以避免路由劫持）。
 *
 * <p>本组件独立实现，不强制重构既有三处 {@code resolveDataSourceOrThrow} 重复（NopMetaTableBizModel profiling /
 * NopMetaQualityRuleBizModel / NopMetaProfilingRuleBizModel）——既有实现行为正确，重复不构成 live defect
 * （见 plan 0800-1 Non-Blocking Follow-up）。
 *
 * <p>失败路径显式化（不静默返回 null、不静默返回 DISABLED 当作可用，对齐 Minimum Rules #24）：
 * <ul>
 *   <li>querySpace null/空/无匹配 → {@link #NopMetadataErrors.ERR_DATASOURCE_RESOLVE_NO_DATASOURCE}</li>
 *   <li>多匹配（历史数据违反 UK）→ {@link #NopMetadataErrors.ERR_DATASOURCE_DUPLICATE_QUERY_SPACE}（AR-03 兜底）</li>
 *   <li>匹配到 DISABLED → {@link #NopMetadataErrors.ERR_DATASOURCE_RESOLVE_DISABLED}</li>
 * </ul>
 *
 * <p>无状态，可在多 BizModel 间共享实例（{@link NopMetaDataSource} DAO 由调用方在调用时通过 {@code daoFor} 获取传入）。
 */
public class MetaDataSourceResolver {

    /** inline ErrorCode：querySpace 为 null/空 或 匹配不到任何 NopMetaDataSource。 */
    /** inline ErrorCode：匹配到的 NopMetaDataSource 处于 DISABLED 状态，不可用于查询执行。 */
    /** AR-03: 多匹配 querySpace（runtime 兜底，应对历史数据违反 ORM 层 UK_NOP_META_DS_QUERY_SPACE）。 */

    /**
     * 解析给定 querySpace 对应的 ACTIVE 数据源。
     *
     * <p>语义对齐 §2.7.1 D1 物理解析路径 + §4.4 D2 + AR-03：使用 {@code findAllByQuery} 检测多匹配，
     * 多匹配显式抛 {@link #NopMetadataErrors.ERR_DATASOURCE_DUPLICATE_QUERY_SPACE}（不取首条，防路由劫持），
     * 找不到/DISABLED 显式失败抛 inline ErrorCode。
     *
     * @param dsDao     数据源 DAO（由调用方通过 {@code daoFor(NopMetaDataSource.class)} 获取）
     * @param querySpace 目标查询空间
     * @return ACTIVE 状态的 NopMetaDataSource（永不 null；不可用时由本方法显式抛出）
     * @throws NopException querySpace null/空/无匹配（{@link #NopMetadataErrors.ERR_DATASOURCE_RESOLVE_NO_DATASOURCE}）、
     *                      多匹配（{@link #NopMetadataErrors.ERR_DATASOURCE_DUPLICATE_QUERY_SPACE}）、
     *                      或 DISABLED（{@link #NopMetadataErrors.ERR_DATASOURCE_RESOLVE_DISABLED}）
     */
    public NopMetaDataSource resolveActiveOrThrow(IEntityDao<NopMetaDataSource> dsDao, String querySpace) {
        if (querySpace == null || querySpace.trim().isEmpty()) {
            // querySpace 为 null/空 → 显式失败（不静默返回 null 当作无数据源）
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_RESOLVE_NO_DATASOURCE)
                    .param("querySpace", String.valueOf(querySpace));
        }
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, querySpace));
        // AR-03: findAllByQuery 后按 size 分派（防多匹配路由劫持；ORM 层 UK 是兜底）
        List<NopMetaDataSource> matched = dsDao.findAllByQuery(q);
        if (matched.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_RESOLVE_NO_DATASOURCE)
                    .param("querySpace", querySpace);
        }
        if (matched.size() > 1) {
            // AR-03: 多匹配（历史数据违反 UK_NOP_META_DS_QUERY_SPACE）→ 拒绝取首条
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_DUPLICATE_QUERY_SPACE)
                    .param("querySpace", querySpace)
                    .param("dataSourceCount", matched.size());
        }
        NopMetaDataSource dataSource = matched.get(0);
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            // DISABLED 数据源不可用于查询执行 → 显式失败（不静默返回 DISABLED 当作可用）
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_RESOLVE_DISABLED)
                    .param("dataSourceId", dataSource.getDataSourceId())
                    .param("querySpace", querySpace);
        }
        return dataSource;
    }
}
