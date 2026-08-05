package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * nop-metadata 模块统一 ErrorCode 常量集（Phase 2 — 集中化迁移）。
 *
 * <p>本接口集中声明跨多个文件使用的 ErrorCode 常量，避免：
 * <ul>
 *   <li>同一错误码字符串在多文件独立 define（消除重复定义）</li>
 *   <li>inline {@code throw new NopException(ErrorCode.define(...))} 用法</li>
 *   <li>ErrorCode 散落在 40+ 文件顶部</li>
 * </ul>
 *
 * <p>命名规范：{@code nop.err.metadata.<子域>-<错误>}（子域与错误名之间用连字符分隔）。
 * 这是该模块的有意选择，与框架核心模块的点分命名不同。子域分组（按字母序）：
 * aggr / catalog / checkpoint / col-lineage / contract / datasource / dialect /
 * dimension / dto / event / field / filter-definition / granularity / join /
 * lineage / link-asset / manifest / measure / module / orm-resource / pagination /
 * profiling / profiling-rule / quality / quality-rule / query / query-filter /
 * recon / score / search / sql / sql-module / sql-type-inference / table /
 * tableref / tag-label.
 *
 * <p>维护说明：新增 ErrorCode 时若使用了新的子域前缀，请同步更新上方列表。
 */
public interface NopMetadataErrors extends AggregationErrors, JoinErrors, QualityErrors,
        DataSourceErrors, SqlErrors, FieldErrors, LineageErrors, ModuleErrors,
        ReconErrors, MiscErrors {
}
