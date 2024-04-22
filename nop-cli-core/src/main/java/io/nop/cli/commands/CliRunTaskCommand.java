package io.nop.cli.commands;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import picocli.CommandLine;

import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "run-task",
        mixinStandardHelpOptions = true,
        description = "运行逻辑编排任务"
)
public class CliRunTaskCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "task.xml逻辑编排任务文件")
    String file;

    @CommandLine.Option(names = {"-i", "--input"}, description = "输入参数")
    String input;


    @SuppressWarnings("unchecked")
    @Override
    public Integer call() throws Exception {
        IResource resource = ResourceHelper.resolveRelativePathResource(file);

        ITaskFlowManager taskFlowManager = BeanContainer.getBeanByType(ITaskFlowManager.class);
        ITask task = taskFlowManager.loadTask(resource);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, new ServiceContextImpl());
        if (input != null) {
            Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(null, input);
            taskRt.getEvalScope().setLocalValues(map);
        }
        Object result = task.execute(taskRt).syncGetResult();
        if (result instanceof Integer)
            return (Integer) result;
        return 0;
    }
}
