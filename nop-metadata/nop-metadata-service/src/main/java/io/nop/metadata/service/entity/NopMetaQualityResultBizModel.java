package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaQualityResultBiz;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.service.NopMetadataErrors;
import jakarta.inject.Inject;

@BizModel("NopMetaQualityResult")
public class NopMetaQualityResultBizModel extends CrudBizModel<NopMetaQualityResult> implements INopMetaQualityResultBiz{

    public NopMetaQualityResultBizModel(){
        setEntityName(NopMetaQualityResult.class.getName());
    }

    /**
     * 同意"已修复"场景：重新执行规则判定。
     * 当前被 {@code qualityBreachApproval} 工作流的 agree 路径通过 c:script 直接调用
     * QualityAlertWorkflowService.reJudge() 处理，此方法保留作为 wf-approval:notifyResult 的回调入口。
     */
    @BizMutation
    public NopMetaQualityResult approve(@Name("id") String id, IServiceContext context) {
        NopMetaQualityResult entity = dao().getEntityById(id);
        if (entity == null) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_RESULT_NOT_FOUND)
                    .param("qualityResultId", id);
        }
        dao().updateEntity(entity);
        return entity;
    }

    /**
     * 驳回"误报"场景：标记 isFalsePositive=true。
     * 由 workflow disagree 路径通过 wf-approval:notifyResult 触发。
     */
    @BizMutation
    public NopMetaQualityResult reject(@Name("id") String id, IServiceContext context) {
        NopMetaQualityResult entity = dao().getEntityById(id);
        if (entity == null) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_RESULT_NOT_FOUND)
                    .param("qualityResultId", id);
        }
        entity.setIsFalsePositive((byte) 1);
        dao().updateEntity(entity);
        return entity;
    }
}
