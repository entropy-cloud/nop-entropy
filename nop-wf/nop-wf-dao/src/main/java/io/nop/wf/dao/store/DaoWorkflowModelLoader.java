/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.dao.store;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.core.resource.component.version.VersionedName;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.store.WfModelParser;
import io.nop.wf.dao.entity.NopWfDefinition;
import jakarta.inject.Inject;

import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ARG_WF_VERSION;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_WF_DEFINITION;
import static io.nop.wf.dao.entity._gen._NopWfDefinition.PROP_NAME_status;
import static io.nop.wf.dao.entity._gen._NopWfDefinition.PROP_NAME_wfName;
import static io.nop.wf.dao.entity._gen._NopWfDefinition.PROP_NAME_wfVersion;

public class DaoWorkflowModelLoader implements IResourceObjectLoader<IWorkflowModel> {

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public IWorkflowModel loadObjectFromPath(String path) {
        VersionedName versionedName = ResourceVersionHelper.parseVersionedName(path, NopWfCoreConstants.RESOLVE_WF_NS_PREFIX);
        NopWfDefinition entity = requireWfDefinition(versionedName.getName(), versionedName.getVersion());

        SourceLocation loc = SourceLocation.fromPath(path);
        XNode node = XNodeParser.instance().parseFromText(loc, entity.getModelText());

        return WfModelParser.parseWorkflowNode(node);
    }

    public NopWfDefinition loadWfDefinition(String wfName, Long wfVersion) {
        Guard.notEmpty(wfName, "wfName");

        IEntityDao<NopWfDefinition> dao = daoProvider.daoFor(NopWfDefinition.class);

        QueryBean query = new QueryBean();
        query.addFilter(eq(PROP_NAME_wfName, wfName));
        query.addFilter(eq(PROP_NAME_status, DaoConstants.ACTIVE_STATUS_ACTIVE));
        if (wfVersion != null && wfVersion > 0) {
            query.addFilter(eq(PROP_NAME_wfVersion, wfVersion));
        }

        query.addOrderField(PROP_NAME_wfVersion, true);
        NopWfDefinition entity = dao.findFirstByQuery(query);

        return entity;
    }

    public NopWfDefinition requireWfDefinition(String wfName, Long wfVersion) {
        NopWfDefinition entity = loadWfDefinition(wfName, wfVersion);
        if (entity == null)
            throw new NopException(ERR_WF_UNKNOWN_WF_DEFINITION)
                    .param(ARG_WF_NAME, wfName)
                    .param(ARG_WF_VERSION, wfVersion);
        return entity;
    }
}