/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.file.core.FileConstants;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.orm.IOrmEntityFileStore;
import io.nop.orm.OrmConstants;
import io.nop.rule.api.beans.RuleKeyBean;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.dao.entity.NopRuleDefinition;
import io.nop.rule.dao.model.DaoRuleModelLoader;
import io.nop.rule.dao.model.DaoRuleModelSaver;
import io.nop.rule.service.NopRuleConstants;
import io.nop.web.page.condition.ConditionSchemaHelper;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.jsonschema.XSchemaToJsonSchema;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@BizModel("NopRuleDefinition")
public class NopRuleDefinitionBizModel extends CrudBizModel<NopRuleDefinition> {
    public NopRuleDefinitionBizModel() {
        setEntityName(NopRuleDefinition.class.getName());
    }

    @Inject
    DaoRuleModelLoader ruleModelLoader;

    @BizQuery
    public ConditionFieldsResponse getConditionFields(
            @Name(NopRuleConstants.RULE_ID_NAME) String ruleId, IServiceContext context) {
        NopRuleDefinition rule = get(ruleId, false, context);
        RuleModel ruleModel = ruleModelLoader.buildRuleModel(rule);
        List<Map<String, Object>> fields = ConditionSchemaHelper.schemaToFields(null, ruleModel.getInputSchema());

        ConditionFieldsResponse ret = new ConditionFieldsResponse();
        ret.setFields(fields);
        return ret;
    }

    @BizQuery
    public DictBean getOutputFields(@Name(NopRuleConstants.RULE_ID_NAME) String ruleId, IServiceContext context) {
        NopRuleDefinition rule = get(ruleId, false, context);
        List<Map<String, Object>> outputs = rule.getRuleOutputs();
        DictBean dict = new DictBean();
        List<DictOptionBean> options = new ArrayList<>();
        if (outputs == null || outputs.isEmpty()) {
            DictOptionBean option = new DictOptionBean();
            option.setLabel(NopRuleConstants.VAR_RESULT);
            option.setValue(NopRuleConstants.VAR_RESULT);
            options.add(option);
        } else {
            for (Map<String, Object> output : outputs) {
                DictOptionBean option = new DictOptionBean();
                String name = (String) output.get(NopRuleConstants.VAR_NAME);
                String displayName = (String) output.get(NopRuleConstants.VAR_DISPLAY_NAME);
                if (displayName == null)
                    displayName = name;
                option.setValue(name);
                option.setLabel(displayName);
                options.add(option);
            }
        }
        dict.setOptions(options);
        return dict;
    }

    @BizQuery
    public WebContentBean getInputJsonSchema(@RequestBean RuleKeyBean ruleKey, IServiceContext context) {
        NopRuleDefinition rule = ruleModelLoader.loadRuleDefinition(ruleKey.getRuleName(), ruleKey.getRuleVersion());
        ISchema schema = ruleModelLoader.buildRuleInputSchema(rule);
        Map<String, Object> jsonSchema = XSchemaToJsonSchema.instance().toJsonSchema(schema, context);
        return WebContentBean.json(jsonSchema);
    }

    @Override
    @BizAction
    protected void defaultPrepareSave(EntityData<NopRuleDefinition> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        importExcelFile(entityData);

        validateModel(entityData.getEntity());
    }

    @Override
    @BizAction
    protected void defaultPrepareUpdate(EntityData<NopRuleDefinition> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);
        importExcelFile(entityData);

        validateModel(entityData.getEntity());
    }

    protected void importExcelFile(EntityData<NopRuleDefinition> entityData) {
        String importFilePath = (String) entityData.getData().get(NopRuleConstants.PROP_IMPORT_FILE);
        if (!StringHelper.isEmpty(importFilePath)) {
            NopRuleDefinition entity = entityData.getEntity();
            IOrmEntityFileStore fileStore = (IOrmEntityFileStore) entityData.getEntity().orm_getBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);
            String fileId = fileStore.decodeFileId(importFilePath);
            // 总是处理上传的临时文件
            String objId = FileConstants.TEMP_BIZ_OBJ_ID;
            IResource resource = fileStore.getFileResource(fileId, getBizObjName(), objId, NopRuleConstants.PROP_IMPORT_FILE);

            RuleModel ruleModel = (RuleModel) ExcelHelper.loadXlsxObject(RuleConstants.IMP_PATH_RULE, resource);
            if (entity.orm_id() == null) {
                dao().initEntityId(entity);
            }
            entity.setRuleType(ruleModel.getRuleType());
            new DaoRuleModelSaver().saveRuleModel(ruleModel, entity);

            fileStore.detachFile(fileId, getBizObjName(), objId, NopRuleConstants.PROP_IMPORT_FILE);
        }
    }

    /**
     * 保存数据后验证一下模型结构正确，可以被解析为RuleModel
     */
    protected void validateModel(NopRuleDefinition entity) {
        this.ruleModelLoader.buildRuleModel(entity);
    }
}
