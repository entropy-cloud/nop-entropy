package io.nop.job.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.job.dao.entity._gen._NopJobTask;

import java.util.LinkedHashMap;
import java.util.Map;


@BizObjName("NopJobTask")
public class NopJobTask extends _NopJobTask {

    /**
     * 返回本任务实际生效的执行参数：以 fire 的 jobParamsSnapshot 为基准，用本任务的
     * taskPayload（task 级参数）覆盖合并，task 级优先。两者皆空时返回空 Map。
     * <p>
     * fire 由调用方显式传入（worker/cancel 路径均已持有）。不依赖 ORM 关联懒加载，
     * 避免对未 orm_attach 的实体（如单元测试中的 new 实例）抛 requireEnhancer 异常。
     */
    public Map<String, Object> getEffectiveParams(NopJobFire fire) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (fire != null) {
            Map<String, Object> fireParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
            if (fireParams != null) {
                result.putAll(fireParams);
            }
        }
        Map<String, Object> taskParams = getTaskPayloadComponent().get_jsonMap();
        if (taskParams != null) {
            result.putAll(taskParams);
        }
        return result;
    }
}
