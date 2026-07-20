
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;
import java.util.Map;


/**
 * NopMetaTable BizModel 契约接口：声明 NopMetaTableBizModel 的全部自定义 public 方法签名。
 *
 * <p>本接口在 ICrudBiz 基础上额外暴露以下方法供跨模块 {@code @Inject INopMetaTableBiz} 调用：
 * profileTable / createSqlTable / previewSqlFields / resolveTableFields /
 * queryTableData / queryJoinData / queryAggregation（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>{@code queryTableData} / {@code queryJoinData} / {@code queryAggregation} 在 plan Phase 1
 * 新增末参 {@code @Optional @Name("selection") FieldSelectionBean selection}（plan 维度12-01）。
 */
public interface INopMetaTableBiz extends ICrudBiz<NopMetaTable> {

    @BizMutation
    Map<String, Object> profileTable(@Name("metaTableId") String metaTableId,
                                      @Optional @Name("schemaPattern") String schemaPattern,
                                      @Optional @Name("columns") String columns,
                                      IServiceContext context);

    @BizMutation
    Map<String, Object> createSqlTable(@Name("sql") String sql,
                                        @Name("tableName") String tableName,
                                        @Name("metaModuleId") String metaModuleId,
                                        @Optional @Name("querySpace") String querySpace,
                                        @Optional @Name("displayName") String displayName,
                                        IServiceContext context);

    @BizQuery
    Map<String, Object> previewSqlFields(@Name("sql") String sql, IServiceContext context);

    @BizQuery
    Map<String, Object> resolveTableFields(@Name("metaTableId") String metaTableId,
                                            IServiceContext context);

    @BizQuery
    Map<String, Object> queryTableData(@Name("metaTableId") String metaTableId,
                                        @Optional @Name("filter") TreeBean filter,
                                        @Optional @Name("limit") Long limit,
                                        @Optional @Name("offset") Long offset,
                                        @Optional @Name("selection") FieldSelectionBean selection,
                                        IServiceContext context);

    @BizQuery
    Map<String, Object> queryJoinData(@Name("metaTableId") String metaTableId,
                                       @Name("joinId") String joinId,
                                       @Optional @Name("filter") TreeBean filter,
                                       @Optional @Name("limit") Long limit,
                                       @Optional @Name("offset") Long offset,
                                       @Optional @Name("selection") FieldSelectionBean selection,
                                       IServiceContext context);

    @BizQuery
    Map<String, Object> queryAggregation(@Name("metaTableId") String metaTableId,
                                          @Name("measures") List<String> measures,
                                          @Name("dimensions") List<String> dimensions,
                                          @Optional @Name("filter") TreeBean filter,
                                          @Optional @Name("joinId") String joinId,
                                          @Optional @Name("limit") Long limit,
                                          @Optional @Name("offset") Long offset,
                                          @Optional @Name("having") TreeBean having,
                                          @Optional @Name("orderBy") List<OrderFieldBean> orderBy,
                                          @Optional @Name("selection") FieldSelectionBean selection,
                                          IServiceContext context);
}
