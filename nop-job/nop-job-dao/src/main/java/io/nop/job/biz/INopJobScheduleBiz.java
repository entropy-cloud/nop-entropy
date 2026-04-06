
package io.nop.job.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.orm.biz.ICrudBiz;
import io.nop.core.context.IServiceContext;

import io.nop.job.dao.entity.NopJobSchedule;

import java.util.Map;

public interface INopJobScheduleBiz extends ICrudBiz<NopJobSchedule>{
    @BizMutation("enableSchedule")
    void enableSchedule(@Name("id") String id, IServiceContext context);

    @BizMutation("disableSchedule")
    void disableSchedule(@Name("id") String id, IServiceContext context);

    @BizMutation("pauseSchedule")
    void pauseSchedule(@Name("id") String id, IServiceContext context);

    @BizMutation("resumeSchedule")
    void resumeSchedule(@Name("id") String id, IServiceContext context);

    @BizMutation("triggerNow")
    void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                    IServiceContext context);

    @BizMutation("archiveSchedule")
    void archiveSchedule(@Name("id") String id, IServiceContext context);
}
