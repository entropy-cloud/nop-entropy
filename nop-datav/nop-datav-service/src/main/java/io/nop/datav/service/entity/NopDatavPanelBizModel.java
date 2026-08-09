package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;

import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.JumpResult;
import io.nop.datav.biz.LinkageResult;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.linkage.LinkageExecutor;
import io.nop.datav.service.query.PanelDataBinder;

import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;

@BizModel("NopDatavPanel")
public class NopDatavPanelBizModel extends CrudBizModel<NopDatavPanel> implements INopDatavPanelBiz {

    @jakarta.inject.Inject
    protected IJdbcTemplate jdbcTemplate;

    public NopDatavPanelBizModel() {
        setEntityName(NopDatavPanel.class.getName());
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavPanel:getPanelData")
    public PanelDataResult getPanelData(@Name("id") String id,
                                        @Name("params") Map<String, Object> requestParams,
                                        IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "getPanelData", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        IDaoProvider daoProvider = daoProvider();
        return new PanelDataBinder(daoProvider, jdbcTemplate).queryPanelData(id, panel, requestParams);
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavPanel:refreshPanel")
    public PanelDataResult refreshPanel(@Name("id") String id, IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "refreshPanel", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        IDaoProvider daoProvider = daoProvider();
        // 复用 getPanelData 的数据绑定管线，触发重新查询并返回最新结果（不重复实现查询逻辑）
        return new PanelDataBinder(daoProvider, jdbcTemplate).queryPanelData(id, panel, null);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavPanel:resolveLinkage")
    public LinkageResult resolveLinkage(@Name("id") String id,
                                        @Name("clickContext") Map<String, Object> clickContext,
                                        IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "resolveLinkage", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        return new LinkageExecutor(daoProvider()).resolveLinkage(id, panel, clickContext);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavPanel:resolveJump")
    public JumpResult resolveJump(@Name("id") String id,
                                  @Name("clickContext") Map<String, Object> clickContext,
                                  IServiceContext context) {
        NopDatavPanel panel = requireEntity(id, "resolveJump", context);
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", id);
        }
        return new LinkageExecutor(daoProvider()).resolveJump(id, panel, clickContext);
    }
}
