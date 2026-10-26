package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.api.dto.AggregationResultDTO;
import io.nop.metadata.api.dto.CreateSqlViewResultDTO;
import io.nop.metadata.api.dto.MethodInvocationResultDTO;
import io.nop.metadata.api.dto.PreviewSqlFieldsResultDTO;
import io.nop.metadata.api.dto.ProfileResultDTO;
import io.nop.metadata.api.dto.QueryEntityDataResultDTO;
import io.nop.metadata.api.dto.QueryJoinDataResultDTO;
import io.nop.metadata.api.dto.ResolveEntityFieldsResultDTO;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.orm.biz.ICrudBiz;

import java.util.List;
import java.util.Map;

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

    /**
     * 方法泛化执行入口（设计 14 §3.4，plan 2026-10-26-2047-1 Phase 3）：加载校验 →
     * args 校验 → roles 检查 → SQL 沙箱 query-time 重验 → entityKind 取数分派。
     */
    @BizQuery
    MethodInvocationResultDTO invokeMethod(@Name("metaEntityId") String metaEntityId,
                                           @Name("methodName") String methodName,
                                           @Optional @Name("args") Map<String, Object> args,
                                           IServiceContext context);

    /** 目录合成只读分页入口（M4，plan 2026-10-26-2047-1 Phase 4）：要求 std-crud/std-crud-ro 标签。 */
    @BizQuery
    QueryEntityDataResultDTO findEntityPage(@Name("metaEntityId") String metaEntityId,
                                            @Optional @Name("query") QueryBean query,
                                            @Optional @Name("selection") FieldSelectionBean selection,
                                            IServiceContext context);

    /** 目录合成只读列表入口：要求 std-crud/std-crud-ro 标签。 */
    @BizQuery
    QueryEntityDataResultDTO findEntityList(@Name("metaEntityId") String metaEntityId,
                                            @Optional @Name("query") QueryBean query,
                                            @Optional @Name("selection") FieldSelectionBean selection,
                                            IServiceContext context);

    /** 目录合成单条入口：按过滤条件取第一行（0..1 items），要求 std-crud/std-crud-ro 标签。 */
    @BizQuery
    QueryEntityDataResultDTO getEntity(@Name("metaEntityId") String metaEntityId,
                                       @Optional @Name("filter") TreeBean filter,
                                       @Optional @Name("selection") FieldSelectionBean selection,
                                       IServiceContext context);
}
