package io.nop.datav.service.linkage;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

import io.nop.datav.biz.LinkageResult;
import io.nop.datav.biz.JumpResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavPanel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_JUMP_TARGET;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;

/**
 * 联动/跳转执行器。从源 Panel 加载 panelConfig → 解析联动/跳转规则 → 按点击上下文字段匹配 → 合成结果。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.2 / §8.3 / §8.5。</p>
 *
 * <p>由 {@code NopDatavPanelBizModel.resolveLinkage} / {@code resolveJump} 调用。</p>
 */
public class LinkageExecutor {

    private final IDaoProvider daoProvider;

    public LinkageExecutor(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * 解析联动：加载源面板 → 解析 linkage 规则 → 按点击字段匹配 → 返回目标面板 + 应用的筛选参数。
     *
     * <p>返回的 params Map key = 联动规则的 {@code targetParam}，value = 点击上下文的字段值。
     * 此格式与 {@code resolveFilterValues} 输出兼容，可直接作为 {@code getPanelData} 的 requestParams
     * （经 paramMapping 的 source 求值后注入 SQL）。</p>
     *
     * @param sourcePanelId 源面板 ID（非空）
     * @param panel         已加载的源面板实体（若为 null，将按 sourcePanelId 加载）
     * @param clickContext  点击上下文 Map（须含 field + value）
     * @return 联动结果；若点击字段不匹配任何规则，返回 null（合法分支）
     */
    public LinkageResult resolveLinkage(String sourcePanelId, NopDatavPanel panel,
                                        Map<String, Object> clickContext) {
        NopDatavPanel source = requirePanel(sourcePanelId, panel);
        String field = requireClickField(sourcePanelId, clickContext);
        Object value = clickContext.get("value");

        List<LinkageRule> rules = LinkageConfigParser.parseLinkageRules(sourcePanelId, source.getPanelConfig());
        LinkageRule matched = null;
        for (LinkageRule rule : rules) {
            if (rule.getSourceField().equals(field)) {
                matched = rule;
                break;
            }
        }
        if (matched == null) {
            // Source field not matched by any rule: legit "no linkage applies" branch -> return null
            return null;
        }

        // Validate target panel exists (cross-panel linkage within same dashboard only)
        NopDatavPanel target = findPanelById(matched.getTargetPanelId());
        if (target == null) {
            throw new NopException(ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND)
                    .param("targetPanelId", matched.getTargetPanelId())
                    .param("panelId", sourcePanelId);
        }
        if (!target.getDashboardId().equals(source.getDashboardId())) {
            // Cross-dashboard linkage is out of scope; use jump instead
            throw new NopException(ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND)
                    .param("targetPanelId", matched.getTargetPanelId())
                    .param("panelId", sourcePanelId);
        }

        // params map: targetParam -> value (compatible with getPanelData requestParams via paramMapping source)
        Map<String, Object> params = new LinkedHashMap<>(1);
        params.put(matched.getTargetParam(), value);
        return new LinkageResult(matched.getTargetPanelId(), params);
    }

    /**
     * 解析跳转：加载源面板 → 解析 jump 规则 → 按点击字段匹配 → 返回跳转目标（targetType + targetId + params）。
     *
     * <p>对 {@code external-url} 类型，{@code targetId} 中的 {@code ${paramName}} 占位符将被替换为
     * 点击上下文中的字段值。对 {@code dashboard} 类型，{@code targetId} 为常量看板 ID（不替换），
     * params 中的 {@code ${sourceField}} 引用将被解析为点击上下文字段值。</p>
     *
     * @param sourcePanelId 源面板 ID（非空）
     * @param panel         已加载的源面板实体（若为 null，将按 sourcePanelId 加载）
     * @param clickContext  点击上下文 Map（须含 field + value）
     * @return 跳转结果；若点击字段不匹配任何规则，返回 null（合法分支）
     */
    public JumpResult resolveJump(String sourcePanelId, NopDatavPanel panel,
                                  Map<String, Object> clickContext) {
        NopDatavPanel source = requirePanel(sourcePanelId, panel);
        String field = requireClickField(sourcePanelId, clickContext);
        Object value = clickContext.get("value");

        List<JumpRule> rules = LinkageConfigParser.parseJumpRules(sourcePanelId, source.getPanelConfig());
        JumpRule matched = null;
        for (JumpRule rule : rules) {
            if (rule.getSourceField().equals(field)) {
                matched = rule;
                break;
            }
        }
        if (matched == null) {
            return null;
        }

        String resolvedTargetId;
        Map<String, Object> resolvedParams;
        if (matched.isExternalUrlType()) {
            // URL template: substitute ${field} markers with click context values
            resolvedTargetId = substituteTemplate(matched.getTargetId(), field, value);
            // external-url params are informational; resolve ${field} references too
            resolvedParams = resolveParams(matched.getParams(), field, value);
        } else {
            // dashboard: targetId is constant dashboardId (no substitution); validate dashboard exists
            validateJumpDashboardTarget(sourcePanelId, matched.getTargetId());
            resolvedTargetId = matched.getTargetId();
            resolvedParams = resolveParams(matched.getParams(), field, value);
        }
        return new JumpResult(matched.getTargetType(), resolvedTargetId, resolvedParams);
    }

    private void validateJumpDashboardTarget(String sourcePanelId, String dashboardId) {
        NopDatavDashboard target = daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        if (target == null) {
            throw new NopException(ERR_DATAV_INVALID_JUMP_TARGET)
                    .param("targetType", "dashboard")
                    .param("targetId", dashboardId)
                    .param("panelId", sourcePanelId);
        }
    }

    private NopDatavPanel requirePanel(String panelId, NopDatavPanel panel) {
        if (panel != null) {
            return panel;
        }
        NopDatavPanel loaded = findPanelById(panelId);
        if (loaded == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", panelId);
        }
        return loaded;
    }

    private NopDatavPanel findPanelById(String panelId) {
        IEntityDao<NopDatavPanel> dao = daoProvider.daoFor(NopDatavPanel.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("panelId", panelId));
        query.setLimit(1);
        return dao.findFirstByQuery(query);
    }

    private static String requireClickField(String panelId, Map<String, Object> clickContext) {
        if (clickContext == null) {
            throw new NopException(ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED)
                    .param("sourceField", "<null>")
                    .param("panelId", panelId);
        }
        Object fieldObj = clickContext.get("field");
        if (!(fieldObj instanceof String) || ((String) fieldObj).isEmpty()) {
            throw new NopException(ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED)
                    .param("sourceField", String.valueOf(fieldObj))
                    .param("panelId", panelId);
        }
        return (String) fieldObj;
    }

    /**
     * 替换 URL 模板中的 {@code ${field}} 占位符为点击上下文的字段值。
     * 仅替换匹配当前点击 field 的占位符（点击上下文只携带一个字段+值）。
     */
    private static String substituteTemplate(String template, String field, Object value) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        String placeholder = "${" + field + "}";
        if (!template.contains(placeholder)) {
            return template;
        }
        return template.replace(placeholder, value == null ? "" : value.toString());
    }

    /**
     * 解析参数 Map 中的 {@code ${field}} 引用为点击上下文字段值。
     * 若 value 引用的不是当前点击字段，保留原引用字符串（不静默替换为空）。
     */
    private static Map<String, Object> resolveParams(Map<String, String> rawParams,
                                                      String field, Object value) {
        if (rawParams == null || rawParams.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>(rawParams.size());
        String placeholder = "${" + field + "}";
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            String paramValue = entry.getValue();
            if (placeholder.equals(paramValue)) {
                result.put(entry.getKey(), value);
            } else {
                // Non-matching reference: pass through as-is (front-end may resolve later)
                result.put(entry.getKey(), paramValue);
            }
        }
        return result;
    }
}
