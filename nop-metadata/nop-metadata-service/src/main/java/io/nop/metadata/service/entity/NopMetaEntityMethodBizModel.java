package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import io.nop.metadata.biz.INopMetaEntityMethodBiz;
import io.nop.metadata.dao.entity.NopMetaEntityMethod;
import io.nop.metadata.service.method.MetaMethodSqlValidator;

import java.util.Map;
import java.util.Set;

/**
 * 实体方法注册表 BizModel（设计 14 §3.1/§3.6，plan 2026-10-26-2047-1 Phase 1）。
 *
 * <p>save 守卫（save/update 双入口对称，命中 fail-loud 事务回滚）：
 * <ul>
 *   <li>methodName 必须为小驼峰标识符且不得与平台标准 action 名冲突（invokeMethod 的
 *       方法名命名空间不得遮蔽宿主 bizObj 的标准 CRUD 面）；</li>
 *   <li>methodKind/implKind 组合：methodKind 限 dict 三值，首版 implKind 仅 sql；</li>
 *   <li>{@code sourceKind=imported} 手写拒绝——快照导入通道独占该值（SSOT 标注，设计结论 6）；
 *       GraphQL save 缺省注入 {@code custom}；</li>
 *   <li>implSource 非空（SQL 沙箱校验在 Phase 3 落地后回接本守卫同点位）。</li>
 * </ul>
 *
 * <p>同实体下 methodName 唯一由 DB UK {@code UK_NOP_META_METHOD_ENTITY_NAME} 承载。
 */
@BizModel("NopMetaEntityMethod")
public class NopMetaEntityMethodBizModel extends CrudBizModel<NopMetaEntityMethod>
        implements INopMetaEntityMethodBiz {

    /**
     * 平台标准 action 名集合（{@code ICrudBiz} + {@code CrudBizModel} 全部公开方法名，
     * 含 @BizAction 管道方法——保留名判定 fail-closed，多拦不漏）。
     */
    public static final Set<String> RESERVED_METHOD_NAMES = Set.of(
            "findPage", "findList", "findFirst", "findCount", "get", "batchGet", "asDict",
            "save", "saveOrUpdate", "copyForNew", "update", "batchUpdate", "updateByQuery",
            "delete", "batchDelete", "batchModify", "deleteByQuery",
            "addManyToManyRelations", "removeManyToManyRelations", "updateManyToManyRelations",
            "findRoots", "findTreeEntityPage", "findTreeEntityList", "findListForTree",
            "findPageForTree", "recoverDeleted", "newEntity", "requireEntity", "deleteEntity",
            "saveEntity", "updateEntity", "assignToEntity", "buildEntityForSave",
            "checkAllowAccess", "batchLoadSelection", "fetchSelection", "fetchSelectionForList",
            "getObjMeta", "getBizObjName");

    private static final Set<String> METHOD_KINDS =
            Set.of("static-query", "static-mutation", "loader");

    /** 首版唯一实现类别（设计 14 §一.5：不支持 xpl/java-bean）。 */
    public static final String IMPL_KIND_SQL = "sql";

    /** sourceKind 缺省值：目录自定义（设计 14 §3.1）。 */
    public static final String SOURCE_KIND_CUSTOM = "custom";

    public NopMetaEntityMethodBizModel() {
        setEntityName(NopMetaEntityMethod.class.getName());
    }

    @Override
    public NopMetaEntityMethod save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // P2-19 先例：null/empty data 提前委托基类（统一 ERR_BIZ_EMPTY_DATA_FOR_SAVE）
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        Map<String, Object> effectiveData = data;
        if (!data.containsKey(NopMetaEntityMethod.PROP_NAME_sourceKind)) {
            // sourceKind 缺省注入 custom（快照导入通道之外的写入口只能产生 custom 行）
            effectiveData = new java.util.HashMap<>(data);
            effectiveData.put(NopMetaEntityMethod.PROP_NAME_sourceKind, SOURCE_KIND_CUSTOM);
        }
        NopMetaEntityMethod saved = super.save(effectiveData, context);
        rejectInvalidMethod(saved);
        return saved;
    }

    @Override
    public NopMetaEntityMethod update(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaEntityMethod updated = super.update(data, context);
        // update 写入面对称防护（改 methodName/methodKind/implSource 撞守卫与 save 同族）
        rejectInvalidMethod(updated);
        return updated;
    }

    /**
     * Phase 1 save 守卫：对 save/update 后的最终实体状态全量校验（部分字段 update 形态
     * 经基类合并后才可见完整列值），命中 fail-loud 抛出，mutation 事务整体回滚
     * （P2-01/P2-28 守卫同语义）。
     */
    private void rejectInvalidMethod(NopMetaEntityMethod method) {
        String methodName = method.getMethodName();
        if (methodName == null || !methodName.matches("[a-z][a-zA-Z0-9]*")) {
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_NAME_INVALID)
                    .param(NopMetadataArgs.ARG_METHOD_NAME, methodName);
        }
        if (RESERVED_METHOD_NAMES.contains(methodName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_NAME_RESERVED)
                    .param(NopMetadataArgs.ARG_METHOD_NAME, methodName);
        }
        String methodKind = method.getMethodKind();
        if (methodKind == null || !METHOD_KINDS.contains(methodKind)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_KIND_UNKNOWN)
                    .param(NopMetadataArgs.ARG_METHOD_KIND, methodKind);
        }
        String implKind = method.getImplKind();
        if (implKind == null || !IMPL_KIND_SQL.equals(implKind)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_IMPL_KIND_UNSUPPORTED)
                    .param(NopMetadataArgs.ARG_IMPL_KIND, implKind);
        }
        String sourceKind = method.getSourceKind();
        if (sourceKind == null || !SOURCE_KIND_CUSTOM.equals(sourceKind)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_SOURCE_IMPORTED_FORBIDDEN)
                    .param(NopMetadataArgs.ARG_SOURCE_KIND, sourceKind);
        }
        String implSource = method.getImplSource();
        if (implSource == null || implSource.trim().isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_METHOD_IMPL_INVALID)
                    .param(NopMetadataArgs.ARG_METHOD_NAME, methodName);
        }
        // Phase 3 回接：save-time SQL 沙箱（与 query-time 同源校验器，双侧同源水位）
        MetaMethodSqlValidator.validateSaveTime(implSource, methodName);
    }
}
