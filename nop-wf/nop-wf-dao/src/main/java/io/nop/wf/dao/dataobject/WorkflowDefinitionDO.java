package io.nop.wf.dao.dataobject;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.dao.entity.NopWfDefinition;
import io.nop.wf.dao.entity.NopWfInstance;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.gt;
import static io.nop.api.core.beans.FilterBeans.lt;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_DEF_ID;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_EMPTY_MODEL_TEXT;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_PARSE_MODEL_TEXT_FAIL;

public class WorkflowDefinitionDO implements IWorkflowDefinitionDO {
    private final IDaoProvider daoProvider;
    private final IOrmTemplate ormTemplate;
    private final IWorkflowManager workflowManager;
    private final NopWfDefinition wfDefinition;
    private final IServiceContext serviceContext;

    public WorkflowDefinitionDO(IDaoProvider daoProvider, IOrmTemplate ormTemplate,
                                IWorkflowManager workflowManager,
                                NopWfDefinition wfDefinition, IServiceContext serviceContext) {
        this.daoProvider = daoProvider;
        this.ormTemplate = ormTemplate;
        this.workflowManager = workflowManager;
        this.wfDefinition = wfDefinition;
        this.serviceContext = serviceContext;
    }

    @Override
    public NopWfDefinition getSourceObject() {
        return wfDefinition;
    }

    @Override
    public long getRunningInstanceCount() {
        IEntityDao<NopWfInstance> dao = daoProvider.daoFor(NopWfInstance.class);
        QueryBean query = getWfQuery();
        query.addFilter(and(
                gt(NopWfInstance.PROP_NAME_status, NopWfCoreConstants.WF_STATUS_SUSPENDED),
                lt(NopWfInstance.PROP_NAME_status, NopWfCoreConstants.WF_STATUS_COMPLETED)));
        return dao.countByQuery(query);
    }

    @Override
    public long getAllInstanceCount() {
        IEntityDao<NopWfInstance> dao = daoProvider.daoFor(NopWfInstance.class);
        QueryBean query = getWfQuery();
        return dao.countByQuery(query);
    }

    @Override
    public long getMaxVersion() {
        SQL sql = SQL.begin().sql("select max(wfVersion)")
                .from().sql(NopWfDefinition.class.getName())
                .where().eq(NopWfDefinition.PROP_NAME_wfName, wfDefinition.getWfName())
                .end();
        return ormTemplate.findLong(sql, 0L);
    }

    protected QueryBean getWfQuery() {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopWfInstance.PROP_NAME_wfName, wfDefinition.getWfName()));
        query.addFilter(FilterBeans.eq(NopWfInstance.PROP_NAME_wfVersion, wfDefinition.getWfVersion()));
        return query;
    }

    @Override
    public IWorkflowModel parseWorkflowModel() {
        String modelText = wfDefinition.getModelText();
        if (StringHelper.isBlank(modelText))
            return null;

        try {
            XNode node = XNodeParser.instance().parseFromText(null, modelText);
            return workflowManager.parseWorkflowNode(node);
        } catch (Exception e) {
            throw new NopException(ERR_WF_PARSE_MODEL_TEXT_FAIL, e)
                    .param(ARG_WF_NAME, wfDefinition.getWfName())
                    .param(ARG_WF_VERSION, wfDefinition.getWfVersion())
                    .param(ARG_WF_DEF_ID, wfDefinition.getWfDefId());
        }
    }

    @Override
    public void validateModel() {
        IWorkflowModel wfModel = parseWorkflowModel();
        if (wfModel == null)
            throw new NopException(ERR_WF_EMPTY_MODEL_TEXT)
                    .param(ARG_WF_NAME, wfDefinition.getWfName())
                    .param(ARG_WF_VERSION, wfDefinition.getWfVersion())
                    .param(ARG_WF_DEF_ID, wfDefinition.getWfDefId());
    }
}