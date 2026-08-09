
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;

import io.nop.datav.biz.FilterState;
import io.nop.datav.biz.INopDatavFilterStateBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavFilterState;
import io.nop.datav.service.linkage.FilterStateCodec;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;

@BizModel("NopDatavFilterState")
public class NopDatavFilterStateBizModel extends CrudBizModel<NopDatavFilterState> implements INopDatavFilterStateBiz {

    public NopDatavFilterStateBizModel() {
        setEntityName(NopDatavFilterState.class.getName());
    }

    @Override
    @BizMutation
    public NopDatavFilterState saveFilterState(@Name("dashboardId") String dashboardId,
                                               @Name("globalFilters") Map<String, Object> globalFilters,
                                               @Name("panelSelections") Map<String, Map<String, Object>> panelSelections,
                                               @Name("urlState") String urlState,
                                               IServiceContext context) {
        // Validate dashboard exists
        NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_DASHBOARD_NOT_FOUND).param("dashboardId", dashboardId);
        }

        String userName = resolveOperator(context);
        String stateContent = FilterStateCodec.encode(globalFilters, panelSelections, urlState);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        NopDatavFilterState existing = findByUserAndDashboard(userName, dashboardId);
        if (existing != null) {
            existing.setStateContent(stateContent);
            existing.setUpdatedBy(userName);
            existing.setUpdateTime(now);
            daoProvider().daoFor(NopDatavFilterState.class).updateEntityDirectly(existing);
            return existing;
        }
        NopDatavFilterState entity = daoProvider().daoFor(NopDatavFilterState.class).newEntity();
        entity.setStateId(generateStateId());
        entity.setUserName(userName);
        entity.setDashboardId(dashboardId);
        entity.setStateContent(stateContent);
        entity.setDelFlag((byte) 0);
        entity.setVersion(0L);
        entity.setCreatedBy(userName);
        entity.setCreateTime(now);
        entity.setUpdatedBy(userName);
        entity.setUpdateTime(now);
        daoProvider().daoFor(NopDatavFilterState.class).saveEntityDirectly(entity);
        return entity;
    }

    @Override
    @BizQuery
    public FilterState getFilterState(@Name("dashboardId") String dashboardId, IServiceContext context) {
        String userName = resolveOperator(context);
        NopDatavFilterState existing = findByUserAndDashboard(userName, dashboardId);
        if (existing == null) {
            // No saved record: return null (caller decides whether to use empty state)
            return null;
        }
        return FilterStateCodec.decode(dashboardId, existing.getStateContent());
    }

    private NopDatavFilterState findByUserAndDashboard(String userName, String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userName", userName));
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavFilterState.class).findFirstByQuery(query);
    }

    private String resolveOperator(IServiceContext context) {
        String userName = null;
        if (context != null) {
            if (context.getUserContext() != null) {
                userName = context.getUserContext().getUserName();
            }
            if ((userName == null || userName.isEmpty()) && context.getContext() != null) {
                userName = context.getContext().getUserName();
            }
        }
        return userName == null || userName.isEmpty() ? "system" : userName;
    }

    private String generateStateId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
