
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavFilterState;

import java.util.Map;

public interface INopDatavFilterStateBiz extends ICrudBiz<NopDatavFilterState> {

    /**
     * 保存当前用户在某看板上的筛选+联动状态。按 当前用户 userName + dashboardId 隔离保存/覆盖：
     * 若该用户+看板已有保存记录，覆盖旧记录；否则新建记录。
     *
     * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §9.4。</p>
     *
     * <p>BizModel 内部按内容契约将 globalFilters + panelSelections + urlState 序列化为
     * {@code stateContent} JSON 存储。</p>
     *
     * @param dashboardId     看板 ID
     * @param globalFilters   全局筛选值 Map（key = 看板参数名，含扁平化复合 key；value = 参数值）
     * @param panelSelections 面板联动选择 Map（key = 源面板 ID；value = {field, value} 对象）
     * @param urlState        URL 序列化形式字符串
     * @param context         服务上下文（用于获取当前用户 userName）
     */
    @BizMutation("saveFilterState")
    NopDatavFilterState saveFilterState(@Name("dashboardId") String dashboardId,
                                        @Name("globalFilters") Map<String, Object> globalFilters,
                                        @Name("panelSelections") Map<String, Map<String, Object>> panelSelections,
                                        @Name("urlState") String urlState,
                                        IServiceContext context);

    /**
     * 恢复当前用户在某看板上的筛选+联动状态。按 当前用户 userName + dashboardId 恢复。
     *
     * <p>**若该用户+看板无保存记录，返回 null**——调用方据此判断无已保存状态，非静默返回空对象。</p>
     *
     * @param dashboardId 看板 ID
     * @param context     服务上下文（用于获取当前用户 userName）
     * @return 结构化的 filter_state；若无保存记录返回 null
     */
    @BizQuery("getFilterState")
    FilterState getFilterState(@Name("dashboardId") String dashboardId,
                               IServiceContext context);
}
