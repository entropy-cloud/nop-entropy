
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;

import io.nop.core.context.IServiceContext;
import io.nop.datav.biz.INopDatavDashboardTabBiz;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavPanel;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 看板页签 BizModel（D0）。
 *
 * <p>P1-09（plan 2026-08-15-2146-3 Phase 3）：标准 {@code delete(id)} / {@code batchDelete} /
 * {@code deleteByQuery} 均虚分派到 {@code doDeleteEntity}，覆写在删除前解绑引用该 tab 的
 * {@code Panel.tabId}（置 null）——裁定「解绑」而非「拒绝删除非空 tab」：Panel.tabId 可空
 * （面板无页签是合法状态），解绑后面板回落到看板默认层而非阻塞页签管理；禁止悬挂引用。
 * 失败异常传播（无静默跳过）。裁定落档 model-design.md。</p>
 */
@BizModel("NopDatavDashboardTab")
public class NopDatavDashboardTabBizModel extends CrudBizModel<NopDatavDashboardTab>
        implements INopDatavDashboardTabBiz {
    public NopDatavDashboardTabBizModel() {
        setEntityName(NopDatavDashboardTab.class.getName());
    }

    @Override
    protected void doDeleteEntity(@Name("entity") NopDatavDashboardTab entity,
                                  @Name("refNamesToCheck") Set<String> refNamesToCheck,
                                  @Name("prepareDelete") BiConsumer<NopDatavDashboardTab, IServiceContext> prepareDelete,
                                  IServiceContext context) {
        unbindPanelsReferencingTab(entity.getTabId());
        super.doDeleteEntity(entity, refNamesToCheck, prepareDelete, context);
    }

    /**
     * 解绑引用该 tab 的面板（tabId 置 null，面板保留）。逐实体 updateEntityDirectly
     * （与 DashboardBizModel 级联更新的实体更新模式一致）。
     */
    private void unbindPanelsReferencingTab(String tabId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("tabId", tabId));
        List<NopDatavPanel> panels = daoProvider().daoFor(NopDatavPanel.class).findAllByQuery(query);
        for (NopDatavPanel panel : panels) {
            panel.setTabId(null);
            daoProvider().daoFor(NopDatavPanel.class).updateEntityDirectly(panel);
        }
    }
}
