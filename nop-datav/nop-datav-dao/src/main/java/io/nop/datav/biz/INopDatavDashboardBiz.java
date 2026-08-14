
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.orm.biz.ICrudBiz;
import io.nop.core.context.IServiceContext;

import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;

import java.util.List;
import java.util.Map;

public interface INopDatavDashboardBiz extends ICrudBiz<NopDatavDashboard>{

    @BizMutation("publishDashboard")
    NopDatavDashboardSnapshot publishDashboard(@Name("id") String id, IServiceContext context);

    @BizQuery("getPublishedDashboard")
    NopDatavDashboardSnapshot getPublishedDashboard(@Name("id") String id, IServiceContext context);

    @BizMutation("rollbackDashboard")
    NopDatavDashboardSnapshot rollbackDashboard(@Name("id") String id, @Name("snapshotVersion") long snapshotVersion,
                                                IServiceContext context);

    @BizQuery("resolveFilterValues")
    Map<String, Object> resolveFilterValues(@Name("id") String id,
                                            @Name("filterValues") Map<String, Object> filterValues,
                                            IServiceContext context);

    @BizQuery("parseFilterFromUrl")
    Map<String, Object> parseFilterFromUrl(@Name("id") String id, @Name("url") String url,
                                           IServiceContext context);

    /**
     * 批量查询看板面板数据。单次调用返回该看板（或指定 panelIds 子集）各面板的数据条目。
     *
     * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §四 批量面板查询。</p>
     *
     * <p>行为：校验看板存活（requireEntity）→ 看板级筛选一次求值（与逐面板
     * {@code resolveFilterValues + getPanelData} 组合语义等价）→ 按 sortOrder 加载面板（可选
     * panelIds 子集过滤，越权引用整体显式报错）→ 上限校验（先于任何查询执行）→ 逐面板复用
     * {@code PanelDataBinder} → 聚合响应。</p>
     *
     * <p>部分失败语义：单个面板查询失败不拖垮整个批量响应，失败面板条目携带
     * {@code success=false + errorCode + errorMessage}；看板级失败（看板不存在、panelIds 越权、
     * 超上限、筛选类型不匹配）整体抛 {@code NopException}。</p>
     *
     * @param id       看板 ID（dashboardId）
     * @param params   原始扁平 key 筛选值 Map（与 {@code resolveFilterValues} 的 filterValues 入参同构），允许 null
     * @param panelIds 可选面板 ID 子集；null/空表示看板全部面板。条目顺序跟随面板 sortOrder，重复 id 去重；
     *                 不存在或不属于该看板的 id 整体显式报错（ERR_DATAV_PANEL_NOT_IN_DASHBOARD）
     * @param context  服务上下文
     */
    @BizQuery("getDashboardData")
    DashboardDataResult getDashboardData(@Name("id") String id,
                                         @Name("params") Map<String, Object> params,
                                         @Name("panelIds") List<String> panelIds,
                                         IServiceContext context);
}
