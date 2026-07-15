package io.nop.metadata.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTableFilterBiz;
import io.nop.metadata.dao.entity.NopMetaTableFilter;

import java.util.List;
import java.util.Map;

/**
 * 表过滤器 BizModel（架构基线 §2.5.2 D1 / plan 0700-2 item 1.5）：基线 CRUD + save 条件结构 + 唯一性校验。
 *
 * <p>save 校验（item 1.1 裁定的 save override 落点）：保存 Filter 时校验：
 * <ul>
 *   <li>{@code definition} JSON 符合 item 1.1 D1 裁定的 TreeBean filter 树条件结构
 *       （{@code JsonTool.parseBeanFromText(definition, TreeBean.class)} 可反序列化）。非法结构显式失败
 *       （不静默存入）。{@code definition} 列为 {@code json-4000}，超 4000 字符由列约束显式失败（不截断）。</li>
 *   <li>{@code isDefault=true} 唯一性（item 1.1 D1 裁定首版强制）：每表至多一个默认过滤器。违反显式失败。</li>
 * </ul>
 *
 * <p>TreeBean filter 树结构（item 1.1 D1）：{@code {type, name?, value?, children?}}——叶子条件由
 * {@link FilterBeans} 构建（eq/ne/gt/ge/lt/le/like/in/between/...），组合条件为 and/or/not。
 */
@BizModel("NopMetaTableFilter")
public class NopMetaTableFilterBizModel extends CrudBizModel<NopMetaTableFilter>
        implements INopMetaTableFilterBiz {

    static final ErrorCode ERR_FILTER_DEFINITION_INVALID =
            ErrorCode.define("metadata.filter-definition-invalid",
                    "Filter definition JSON is not a valid TreeBean filter tree: {metaTableId} filterName={filterName}",
                    "metaTableId", "filterName");
    static final ErrorCode ERR_FILTER_DEFINITION_EMPTY =
            ErrorCode.define("metadata.filter-definition-empty",
                    "Filter definition is empty: {metaTableId} filterName={filterName}",
                    "metaTableId", "filterName");
    static final ErrorCode ERR_FILTER_DEFAULT_ALREADY_EXISTS =
            ErrorCode.define("metadata.filter-default-already-exists",
                    "Only one default filter (isDefault=true) is allowed per table: "
                            + "{metaTableId} existingDefault={existingFilterId}",
                    "metaTableId", "existingFilterId");

    /** TreeBean 反序列化目标类型（item 1.1 D1 裁定：对齐平台 TreeBean filter 树）。 */
    private static final Class<TreeBean> DEFINITION_TYPE = TreeBean.class;

    public NopMetaTableFilterBizModel() {
        setEntityName(NopMetaTableFilter.class.getName());
    }

    /**
     * save override（item 1.1 裁定的 save override 新模式）：持久化前校验 definition 结构 + isDefault 唯一性。
     *
     * <p>校验通过后委托 {@code super.save(...)} 走默认持久化逻辑。非法结构/唯一性违反显式抛 ErrorCode
     * （不静默存入、不静默跳过）。
     */
    @Override
    public NopMetaTableFilter save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String metaTableId = stringOf(data, NopMetaTableFilter.PROP_NAME_metaTableId);
        String filterName = stringOf(data, NopMetaTableFilter.PROP_NAME_filterName);
        String definition = stringOf(data, NopMetaTableFilter.PROP_NAME_definition);
        boolean isDefault = booleanOf(data, NopMetaTableFilter.PROP_NAME_isDefault);
        String selfFilterId = stringOf(data, NopMetaTableFilter.PROP_NAME_filterId);

        validateDefinition(metaTableId, filterName, definition);
        if (isDefault) {
            validateDefaultUnique(metaTableId, selfFilterId);
        }
        return super.save(data, context);
    }

    /**
     * 校验 definition JSON 可反序列化为 {@link TreeBean}（item 1.1 D1 裁定的 TreeBean filter 树结构）。
     *
     * <p>反序列化失败/结构非法（非 JSON、非对象、tagName 非法）显式失败。{@code definition} 为空时显式失败
     * （definition 列为 mandatory）。
     */
    private void validateDefinition(String metaTableId, String filterName, String definition) {
        if (definition == null || definition.trim().isEmpty()) {
            throw new NopException(ERR_FILTER_DEFINITION_EMPTY)
                    .param("metaTableId", metaTableId).param("filterName", filterName);
        }
        try {
            // item 1.1 D1 裁定：对齐平台 TreeBean filter 树（非整个 QueryBean，过滤是其 filter 子树）
            TreeBean tree = JsonTool.parseBeanFromText(definition, DEFINITION_TYPE);
            if (tree == null || tree.getTagName() == null || tree.getTagName().isEmpty()) {
                throw new NopException(ERR_FILTER_DEFINITION_INVALID)
                        .param("metaTableId", metaTableId).param("filterName", filterName);
            }
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_FILTER_DEFINITION_INVALID)
                    .param("metaTableId", metaTableId).param("filterName", filterName).cause(e);
        }
    }

    /**
     * 校验 isDefault 唯一性（item 1.1 D1 裁定首版强制）：每表至多一个 isDefault=true 的过滤器。
     * {@code selfFilterId} 为当前正在保存的过滤器 ID（update 场景排除自身）。
     */
    private void validateDefaultUnique(String metaTableId, String selfFilterId) {
        if (metaTableId == null || metaTableId.isEmpty()) {
            return;
        }
        IEntityDao<NopMetaTableFilter> filterDao = dao();
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableFilter.PROP_NAME_metaTableId, metaTableId));
        q.addFilter(FilterBeans.eq(NopMetaTableFilter.PROP_NAME_isDefault, true));
        List<NopMetaTableFilter> existingDefaults = filterDao.findAllByQuery(q);
        for (NopMetaTableFilter existing : existingDefaults) {
            // update 自身时排除（selfFilterId 非空且等于已存在默认过滤器的 filterId）
            if (selfFilterId != null && selfFilterId.equals(existing.getFilterId())) {
                continue;
            }
            throw new NopException(ERR_FILTER_DEFAULT_ALREADY_EXISTS)
                    .param("metaTableId", metaTableId)
                    .param("existingFilterId", existing.getFilterId());
        }
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean booleanOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }
}
