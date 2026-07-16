package io.nop.metadata.service.datasource;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;

/**
 * querySpace→{@link NopMetaDataSource} 解析共享组件（架构基线 §4.4 D2）：按 {@code NopMetaDataSource.querySpace == 目标 querySpace}
 * 的 {@code findFirstByQuery} 取**首条**匹配（多匹配取首条，首版不强制唯一性、不记 warning，与 §2.7.1 D1 现状一致）。
 *
 * <p>本组件独立实现，不强制重构既有三处 {@code resolveDataSourceOrThrow} 重复（NopMetaTableBizModel profiling /
 * NopMetaQualityRuleBizModel / NopMetaProfilingRuleBizModel）——既有实现行为正确，重复不构成 live defect
 * （见 plan 0800-1 Non-Blocking Follow-up）。
 *
 * <p>失败路径显式化（不静默返回 null、不静默返回 DISABLED 当作可用，对齐 Minimum Rules #24）：
 * <ul>
 *   <li>querySpace null/空/无匹配 → {@link #ERR_RESOLVE_NO_DATASOURCE}</li>
 *   <li>匹配到 DISABLED → {@link #ERR_RESOLVE_DATASOURCE_DISABLED}</li>
 * </ul>
 *
 * <p>无状态，可在多 BizModel 间共享实例（{@link NopMetaDataSource} DAO 由调用方在调用时通过 {@code daoFor} 获取传入）。
 */
public class MetaDataSourceResolver {

    /** inline ErrorCode：querySpace 为 null/空 或 匹配不到任何 NopMetaDataSource。 */
    static final ErrorCode ERR_RESOLVE_NO_DATASOURCE =
            ErrorCode.define("metadata.datasource-resolve-not-found",
                    "No registered MetaDataSource for querySpace: {querySpace}", "querySpace");
    /** inline ErrorCode：匹配到的 NopMetaDataSource 处于 DISABLED 状态，不可用于查询执行。 */
    static final ErrorCode ERR_RESOLVE_DATASOURCE_DISABLED =
            ErrorCode.define("metadata.datasource-resolve-disabled",
                    "MetaDataSource is DISABLED, cannot be used for query execution: {dataSourceId} querySpace={querySpace}",
                    "dataSourceId", "querySpace");

    /**
     * 解析给定 querySpace 对应的 ACTIVE 数据源。
     *
     * <p>语义对齐 §2.7.1 D1 物理解析路径 + §4.4 D2：{@code findFirstByQuery} 首条（多匹配取首条），
     * 找不到/DISABLED 显式失败抛 inline ErrorCode。
     *
     * @param dsDao     数据源 DAO（由调用方通过 {@code daoFor(NopMetaDataSource.class)} 获取）
     * @param querySpace 目标查询空间
     * @return ACTIVE 状态的 NopMetaDataSource（永不 null；不可用时由本方法显式抛出）
     * @throws NopException querySpace null/空/无匹配（{@link #ERR_RESOLVE_NO_DATASOURCE}）或 DISABLED（{@link #ERR_RESOLVE_DATASOURCE_DISABLED}）
     */
    public NopMetaDataSource resolveActiveOrThrow(IEntityDao<NopMetaDataSource> dsDao, String querySpace) {
        if (querySpace == null || querySpace.trim().isEmpty()) {
            // querySpace 为 null/空 → 显式失败（不静默返回 null 当作无数据源）
            throw new NopException(ERR_RESOLVE_NO_DATASOURCE)
                    .param("querySpace", String.valueOf(querySpace));
        }
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, querySpace));
        // 多匹配取首条（findFirstByQuery，§4.4 D2 + §2.7.1 D1 现状；首版不记 warning）
        NopMetaDataSource dataSource = dsDao.findFirstByQuery(q);
        if (dataSource == null) {
            throw new NopException(ERR_RESOLVE_NO_DATASOURCE)
                    .param("querySpace", querySpace);
        }
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            // DISABLED 数据源不可用于查询执行 → 显式失败（不静默返回 DISABLED 当作可用）
            throw new NopException(ERR_RESOLVE_DATASOURCE_DISABLED)
                    .param("dataSourceId", dataSource.getDataSourceId())
                    .param("querySpace", querySpace);
        }
        return dataSource;
    }
}
