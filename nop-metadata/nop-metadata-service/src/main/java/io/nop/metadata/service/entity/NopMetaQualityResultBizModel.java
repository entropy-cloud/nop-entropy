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
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;

@BizModel("NopMetaQualityResult")
public class NopMetaQualityResultBizModel extends CrudBizModel<NopMetaQualityResult> implements INopMetaQualityResultBiz{

    public NopMetaQualityResultBizModel(){
        setEntityName(NopMetaQualityResult.class.getName());
    }

    /**
     * 同意（"已修复"）回调入口：**无字段变更、不做 re-judge**——仅 requireEntity 加载并返回实体。
     *
     * <p>P2-22（plan 2026-08-16-0226-3）语义诚实化：真实的 re-judge 由
     * {@code qualityBreachApproval} 工作流 agree 路径的 verify 步骤完成
     * （v1.xwf c:script 调 {@code QualityAlertWorkflowProcessor.reJudgeFailClosed}，
     * 先于流程结束）；本方法仅作为 wf-approval:notifyResult 约定的 approve 回调挂点保留
     * （qualityBreachApproval v1.xwf 当前未挂 notifyResult listener，agree 结束路径不
     * 触发本方法；disagree 路径由 onDisagree listener 直改实体标记误报）。此前方法体中
     * 无字段变更的 {@code updateEntity} 调用已删除（无可论证目的——无乐观锁/时间戳诉求，
     * 实体未变更）。
     */
    @BizMutation
    public NopMetaQualityResult approve(@Name("id") String id, IServiceContext context) {
        return requireEntity(id, "approve", context);
    }

    /**
     * 驳回"误报"场景：标记 isFalsePositive=true。
     * 由 workflow disagree 路径通过 wf-approval:notifyResult 触发。
     */
    @BizMutation
    public NopMetaQualityResult reject(@Name("id") String id, IServiceContext context) {
        NopMetaQualityResult entity = requireEntity(id, "reject", context);
        entity.setIsFalsePositive((byte) 1);
        dao().updateEntity(entity);
        return entity;
    }
}
