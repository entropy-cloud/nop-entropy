
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

    /**
     * 导出看板布局 JSON（flux dashboard editor 可直接加载的 DashboardLayoutSchema 形态）。
     *
     * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §九 flux 布局对齐契约。</p>
     *
     * <p>行为：读取编辑态 live 数据（主表 layoutConfig + 按 sortOrder 排序的面板行）→ 类型词表映射
     * （{@code PanelTypeMapping}）→ 有几何面板读存储几何、无几何面板默认布局合成（§9.5）→
     * props 去 dataBinding 保护区 → 绑定面板携带 {@code source=datasetRef:<datasetRefId>}（§9.9）。</p>
     *
     * @param id      看板 ID（dashboardId）
     * @param context 服务上下文
     */
    @BizQuery("exportDashboardLayout")
    Map<String, Object> exportDashboardLayout(@Name("id") String id, IServiceContext context);

    /**
     * 保存 flux 编辑器产出的布局 JSON，reconcile（增/删/改）回归一化面板行。
     *
     * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §九 flux 布局对齐契约。</p>
     *
     * <p>行为：载荷结构校验（布局对象形态，panels 必填；§9.7）→ 面板上界校验（§9.10）→
     * 身份对齐（命中既有 panelId=更新/未命中=服务端新建；重复 id、跨看板 id 显式报错；§9.2）→
     * 几何/网格参数/props 按契约持久化（dataBinding 唯一保护区；绑定只保留不建立；§9.4/9.6/9.9）→
     * 单事务落库 → 返回再导出的布局 JSON（编辑器以响应重同步面板 id）。</p>
     *
     * <p>校验失败零落库（全部校验先于任何写入）；删除面板的 AlertRule 置 DISABLED 并注销 cron
     * （镜像看板删除级联先例）。</p>
     *
     * @param id      看板 ID（dashboardId）
     * @param layout  布局对象 {@code {panels:[...], cols?, rowHeight?, gap?, height?}}（网格参数缺省=保留不动）
     * @param context 服务上下文
     */
    @BizMutation("saveDashboardLayout")
    Map<String, Object> saveDashboardLayout(@Name("id") String id,
                                            @Name("layout") Map<String, Object> layout,
                                            IServiceContext context);
}
