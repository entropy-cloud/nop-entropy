package io.nop.job.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.job.api.log.TaskLogEntry;
import io.nop.orm.biz.ICrudBiz;
import io.nop.job.dao.entity.NopJobTaskLog;

import java.util.List;

public interface INopJobTaskLogBiz extends ICrudBiz<NopJobTaskLog> {

    /**
     * 批量接收任务日志行并落库（plan 2254，可选行为）。返回接收条数。
     */
    @BizMutation("reportTaskLog")
    int reportTaskLog(@Name("entries") List<TaskLogEntry> entries, IServiceContext context);
}
