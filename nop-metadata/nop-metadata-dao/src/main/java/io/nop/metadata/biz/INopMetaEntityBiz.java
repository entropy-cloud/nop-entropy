package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.api.dto.CreateSqlViewResultDTO;
import io.nop.metadata.api.dto.PreviewSqlFieldsResultDTO;
import io.nop.metadata.api.dto.ProfileResultDTO;
import io.nop.metadata.api.dto.QueryEntityDataResultDTO;
import io.nop.metadata.api.dto.QueryJoinDataResultDTO;
import io.nop.metadata.api.dto.ResolveEntityFieldsResultDTO;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;

/**
 * NopMetaEntity BizModel 契约接口：声明 NopMetaEntityBizModel 的全部自定义 public 方法签名。
 *
 * <p>概念缩减（plan 2261）：原逻辑表 Biz 接口的能力并入本接口——
 * profileEntity（原 profileTable）/ createSqlView（原 createSqlTable）/ previewSqlFields /
 * resolveEntityFields（原 resolveTableFields）/ queryData（原 queryTableData）/ queryJoinData / queryAggregation。
 *
 * <p>接口返回具体 {@code @DataBean} DTO 供 GraphQL schema 推导强类型字段。
 */
public interface INopMetaEntityBiz extends ICrudBiz<NopMetaEntity> {

    @BizMutation
    ProfileResultDTO profileEntity(@Name("metaEntityId") String metaEntityId,
                                   @Optional @Name("schemaPattern") String schemaPattern,
                                   @Optional @Name("columns") String columns,
                                   IServiceContext context);

    @BizMutation
    CreateSqlViewResultDTO createSqlView(@Name("sql") String sql,
                                         @Name("entityName") String entityName,
                                         @Name("metaModuleId") String metaModuleId,
                                         @Optional @Name("querySpace") String querySpace,
                                         @Optional @Name("displayName") String displayName,
                                         IServiceContext context);

    @BizQuery
    PreviewSqlFieldsResultDTO previewSqlFields(@Name("sql") String sql, IServiceContext context);

    @BizQuery
    ResolveEntityFieldsResultDTO resolveEntityFields(@Name("metaEntityId") String metaEntityId,
                                                     IServiceContext context);

    @BizQuery
    QueryEntityDataResultDTO queryData(@Name("metaEntityId") String metaEntityId,
                                       @Optional @Name("filter") TreeBean filter,
                                       @Optional @Name("limit") Long limit,
                                       @Optional @Name("offset") Long offset,
                                       @Optional @Name("selection") FieldSelectionBean selection,
                                       IServiceContext context);

    @BizQuery
    QueryJoinDataResultDTO queryJoinData(@Name("metaEntityId") String metaEntityId,
                                         @Name("joinId") String joinId,
                                         @Optional @Name("filter") TreeBean filter,
                                         @Optional @Name("limit") Long limit,
                                         @Optional @Name("offset") Long offset,
                                         @Optional @Name("selection") FieldSelectionBean selection,
                                         IServiceContext context);

    @BizQuery
    AggregationResultDTO queryAggregation(@Name("metaEntityId") String metaEntityId,
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
