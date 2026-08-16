package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavPanel;

import java.util.Map;

public interface INopDatavPanelBiz extends ICrudBiz<NopDatavPanel> {

    /**
     * 查询面板数据。返回结构化结果（列名列表 + 行列表 + hasDataset 标志）。
     *
     * <p>对于有数据集绑定的组件（chart/table/pivot-table/stat-tile/map），执行查询并回传结果；
     * 对于无数据集组件（text/iframe/container），返回 {@code hasDataset=false}（非静默跳过）。</p>
     *
     * @param id            面板 ID（panelId）
     * @param requestParams 可选请求参数 Map；用于 paramMapping 求值。允许 null/空（无参数查询）
     * @param context       服务上下文
     */
    @BizQuery("getPanelData")
    PanelDataResult getPanelData(@Name("id") String id,
                                 @Name("params") Map<String, Object> requestParams,
                                 IServiceContext context);

    /**
     * 手动刷新面板数据。复用 {@link #getPanelData} 的查询管线，触发重新查询并返回最新结果。
     *
     * @param id 面板 ID（panelId）
     */
    @BizMutation("refreshPanel")
    PanelDataResult refreshPanel(@Name("id") String id, IServiceContext context);

    /**
     * 解析联动。接收源面板 ID + 点击上下文 → 加载源面板联动配置 → 按 {@code sourceField} 匹配规则 →
     * 返回目标面板 ID + 应用的筛选参数 Map。
     *
     * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.5。</p>
     *
     * <p>返回的 params Map 格式与 {@code resolveFilterValues} 输出兼容，可直接作为 {@code getPanelData}
     * 的 requestParams。**若源字段不匹配任何规则，返回 null**（前端据此判断无联动可应用，属正常分支）；
     * 若联动配置格式错误或目标面板不存在，显式抛 {@code NopException}。</p>
     *
     * @param id           源面板 ID（panelId）
     * @param clickContext 点击上下文 Map，至少包含 {@code field}（被点击的字段名）和 {@code value}（字段值）
     * @param context      服务上下文
     */
    @BizQuery("resolveLinkage")
    LinkageResult resolveLinkage(@Name("id") String id,
                                 @Name("clickContext") Map<String, Object> clickContext,
                                 IServiceContext context);

    /**
     * 解析跳转。接收源面板 ID + 点击上下文 → 加载跳转配置 → 匹配跳转规则 → 返回跳转目标
     * （targetType + targetId + params）。
     *
     * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §8.5。URL 模板中的 {@code ${paramName}}
     * 占位符将被替换为点击上下文中的字段值。</p>
     *
     * <p>**若源字段不匹配任何规则，返回 null**（前端据此判断无跳转可应用，属正常分支）；
     * 若跳转配置格式错误或目标无效，显式抛 {@code NopException}。</p>
     *
     * @param id           源面板 ID（panelId）
     * @param clickContext 点击上下文 Map，至少包含 {@code field}（被点击的字段名）和 {@code value}（字段值）
     * @param context      服务上下文
     */
    @BizQuery("resolveJump")
    JumpResult resolveJump(@Name("id") String id,
                           @Name("clickContext") Map<String, Object> clickContext,
                           IServiceContext context);
}
