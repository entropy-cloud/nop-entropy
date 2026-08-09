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
}
