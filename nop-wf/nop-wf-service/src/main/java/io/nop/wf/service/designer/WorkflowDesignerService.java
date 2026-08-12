/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.designer;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.dao.dataobject.IWorkflowDOProvider;
import io.nop.wf.dao.entity.NopWfDefinition;
import io.nop.wf.core.store.WfModelParser;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.wf.service.designer.NopWfDesignerErrors.ARG_DETAIL;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ARG_WF_DEF_ID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_DEFINITION_PUBLISHED;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_INVALID_DOCUMENT;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_MODEL_INVALID;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_MODEL_PARSE_FAILED;
import static io.nop.wf.service.designer.NopWfDesignerErrors.ERR_WF_DESIGNER_UNKNOWN_DEFINITION;

/**
 * 工作流设计器服务（设计契约 ai-dev/design/nop-wf/workflow-designer-integration.md §3.4）。
 *
 * <p>{@code loadDesignerPage} 返回 designer-page schema（type/config/document/toolbar），
 * {@code saveDocument} 接收设计器导出的 GraphDocument JSON，经
 * {@link WfGraphDocumentCodec} 回写原始 XNode 后走**引擎加载路径**校验
 * （WfModelParser + DAG，与 DaoWorkflowModelLoader 同源），校验通过才落库。
 */
@BizModel("WorkflowDesignerService")
public class WorkflowDesignerService {

    private IDaoProvider daoProvider;

    private IWorkflowDOProvider workflowDOProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setWorkflowDOProvider(IWorkflowDOProvider workflowDOProvider) {
        this.workflowDOProvider = workflowDOProvider;
    }

    protected IEntityDao<NopWfDefinition> definitionDao() {
        return daoProvider.daoFor(NopWfDefinition.class);
    }

    private NopWfDefinition requireDefinition(String wfDefId) {
        NopWfDefinition entity = definitionDao().getEntityById(wfDefId);
        if (entity == null)
            throw new NopException(ERR_WF_DESIGNER_UNKNOWN_DEFINITION).param(ARG_WF_DEF_ID, wfDefId);
        return entity;
    }

    private void checkNotPublished(NopWfDefinition entity) {
        if (entity.getStatus() != null && entity.getStatus() == WfDesignerConstants.WF_STATUS_PUBLISHED)
            throw new NopException(ERR_WF_DESIGNER_DEFINITION_PUBLISHED).param(ARG_WF_DEF_ID, entity.getWfDefId());
    }

    private XNode loadWorkflowNode(NopWfDefinition entity) {
        String modelText = entity.getModelText();
        if (StringHelper.isBlank(modelText))
            return WfGraphDocumentCodec.newWorkflowNode(entity.getWfName(), entity.getWfVersion());
        try {
            return XNodeParser.instance().parseFromText(null, modelText);
        } catch (Exception e) {
            throw new NopException(ERR_WF_DESIGNER_MODEL_PARSE_FAILED)
                    .param(ARG_WF_DEF_ID, entity.getWfDefId())
                    .param(ARG_DETAIL, e.getMessage());
        }
    }

    /**
     * 返回 designer-page schema。modelText 为空时返回初始空图（仅 start/end 合成节点）。
     */
    @BizQuery("loadDesignerPage")
    public Map<String, Object> loadDesignerPage(@Name("wfDefId") String wfDefId) {
        NopWfDefinition entity = requireDefinition(wfDefId);
        XNode workflow = loadWorkflowNode(entity);
        Map<String, Object> document = WfGraphDocumentCodec.workflowToDocument(workflow, wfDefId);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "designer-page");
        schema.put("config", WfDesignerConfigBuilder.buildConfig());
        schema.put("document", document);
        // page 级 toolbar region：有内容时整体替换内置工具栏（装配面裁定 2026-08-10）
        schema.put("toolbar", WfDesignerConfigBuilder.buildToolbarSchema(wfDefId, false));
        return schema;
    }

    /**
     * 保存设计器文档：GraphDocument JSON → 原始 XNode 回写 → 引擎加载路径校验 → modelText 落库。
     * 校验失败（含 DAG 有环）快速失败，不写库。
     */
    @BizMutation("saveDocument")
    public Map<String, Object> saveDocument(@Name("wfDefId") String wfDefId, @Name("doc") String docJson,
                                            IServiceContext context) {
        NopWfDefinition entity = requireDefinition(wfDefId);
        checkNotPublished(entity);

        Object parsed;
        try {
            parsed = JsonTool.parse(docJson);
        } catch (Exception e) {
            throw new NopException(ERR_WF_DESIGNER_INVALID_DOCUMENT)
                    .param(ARG_DETAIL, e.getMessage());
        }
        if (!(parsed instanceof Map))
            throw new NopException(ERR_WF_DESIGNER_INVALID_DOCUMENT)
                    .param(ARG_DETAIL, "document must be a JSON object");
        Map<String, Object> doc = (Map<String, Object>) parsed;

        XNode workflow = loadWorkflowNode(entity);
        WfGraphDocumentCodec.updateWorkflowFromDocument(doc, workflow);

        // 引擎加载路径校验（DslModelParser 自动触发 INeedInit.init() → WfModelAnalyzer DAG 检查）
        try {
            WfModelParser.parseWorkflowNode(workflow);
        } catch (NopException e) {
            throw new NopException(ERR_WF_DESIGNER_MODEL_INVALID)
                    .param(ARG_WF_DEF_ID, wfDefId)
                    .param(ARG_DETAIL, e.getMessage());
        }

        entity.setModelText(workflow.xml());
        definitionDao().updateEntity(entity);

        // 写后复核（设计文档 §3.4：写后调用 IWorkflowDefinitionDO.validateModel()）
        workflowDOProvider.getWorkflowDefinitionDO(entity, context).validateModel();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("wfDefId", wfDefId);
        return result;
    }
}
