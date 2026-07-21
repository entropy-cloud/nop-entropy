
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.core.dto.AggregationResultDTO;
import io.nop.metadata.core.dto.CreateSqlTableResultDTO;
import io.nop.metadata.core.dto.PreviewSqlFieldsResultDTO;
import io.nop.metadata.core.dto.ProfileResultDTO;
import io.nop.metadata.core.dto.QueryJoinDataResultDTO;
import io.nop.metadata.core.dto.QueryTableDataResultDTO;
import io.nop.metadata.core.dto.ResolveTableFieldsResultDTO;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;


/**
 * NopMetaTable BizModel 契约接口：声明 NopMetaTableBizModel 的全部自定义 public 方法签名。
 *
 * <p>本接口在 ICrudBiz 基础上额外暴露以下方法供跨模块 {@code @Inject INopMetaTableBiz} 调用：
 * profileTable / createSqlTable / previewSqlFields / resolveTableFields /
 * queryTableData / queryJoinData / queryAggregation。
 *
 * <p>接口返回具体 {@code @DataBean} DTO 供 GraphQL schema 推导强类型字段。
 */
public interface INopMetaTableBiz extends ICrudBiz<NopMetaTable> {

    @BizMutation
    ProfileResultDTO profileTable(@Name("metaTableId") String metaTableId,
                                  @Optional @Name("schemaPattern") String schemaPattern,
                                  @Optional @Name("columns") String columns,
                                  IServiceContext context);

    @BizMutation
    CreateSqlTableResultDTO createSqlTable(@Name("sql") String sql,
                                           @Name("tableName") String tableName,
                                           @Name("metaModuleId") String metaModuleId,
                                           @Optional @Name("querySpace") String querySpace,
                                           @Optional @Name("displayName") String displayName,
                                           IServiceContext context);

    @BizQuery
    PreviewSqlFieldsResultDTO previewSqlFields(@Name("sql") String sql, IServiceContext context);

    @BizQuery
    ResolveTableFieldsResultDTO resolveTableFields(@Name("metaTableId") String metaTableId,
                                                    IServiceContext context);

    @BizQuery
    QueryTableDataResultDTO queryTableData(@Name("metaTableId") String metaTableId,
                                           @Optional @Name("filter") TreeBean filter,
                                           @Optional @Name("limit") Long limit,
                                           @Optional @Name("offset") Long offset,
                                           @Optional @Name("selection") FieldSelectionBean selection,
                                           IServiceContext context);

    @BizQuery
    QueryJoinDataResultDTO queryJoinData(@Name("metaTableId") String metaTableId,
                                         @Name("joinId") String joinId,
                                         @Optional @Name("filter") TreeBean filter,
                                         @Optional @Name("limit") Long limit,
                                         @Optional @Name("offset") Long offset,
                                         @Optional @Name("selection") FieldSelectionBean selection,
                                         IServiceContext context);

    @BizQuery
    AggregationResultDTO queryAggregation(@Name("metaTableId") String metaTableId,
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
