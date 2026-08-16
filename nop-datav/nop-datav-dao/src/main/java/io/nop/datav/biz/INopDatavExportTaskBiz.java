
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavExportTask;

import java.util.Map;

public interface INopDatavExportTaskBiz extends ICrudBiz<NopDatavExportTask> {

    @BizMutation("createExportTask")
    NopDatavExportTask createExportTask(@Name("sourceType") String sourceType,
                                        @Name("sourceId") String sourceId,
                                        @Name("format") String format,
                                        @Name("params") Map<String, Object> params,
                                        IServiceContext context);

    @BizQuery("getExportTask")
    NopDatavExportTask getExportTask(@Name("taskId") String taskId,
                                     IServiceContext context);

    @BizMutation("cancelExportTask")
    NopDatavExportTask cancelExportTask(@Name("taskId") String taskId,
                                        IServiceContext context);

    @BizQuery("downloadExportFile")
    IResource downloadExportFile(@Name("taskId") String taskId,
                                 IServiceContext context);
}
