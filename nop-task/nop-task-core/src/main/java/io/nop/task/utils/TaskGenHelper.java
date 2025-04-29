package io.nop.task.utils;

import io.nop.core.lang.xml.XNode;
import io.nop.task.model.TaskFlowModel;
import io.nop.xlang.xdsl.action.BizActionGenHelper;

import java.util.stream.Collectors;

public class TaskGenHelper {

    public static XNode buildBizActionFromTaskModel(XNode actionNode, TaskFlowModel taskFlowModel) {
        return BizActionGenHelper.buildBizActionFromActionModel(actionNode, taskFlowModel, (argNames, useResult) -> {

            String source = "\nconst taskFlowManager = inject('nopTaskFlowManager');\n" +
                    "const task = taskFlowManager.getTask('" + taskFlowModel.getName() +
                    "'," + taskFlowModel.getVersion() + ");\n" +
                    "const taskRt = taskFlowManager.newTaskRuntime(task,"
                    + taskFlowModel.isDefaultSaveState() + ",svcCtx);\n"
                    + argNames.stream().map(argName -> "taskRt.setInput('" + argName + "'," + argName + ");").collect(Collectors.joining("\n"))
                    + buildReturnCode(useResult);
            return source;
        });
    }

    // 如果task的输出定义中有名为RESULT的变量，则以它为返回值，否则以整个Outputs为返回值
    private static String buildReturnCode(boolean useResult) {
        if (useResult) {
            return "\nreturn task.execute(taskRt).getResultValuePromise();\n";
        } else {
            return "\nreturn task.execute(taskRt,_selection?.sourceFields).asyncOutputs();\n";
        }
    }

}