package io.nop.cli.commands;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.ioc.api.BeanScopeContext;
import io.nop.ioc.api.IBeanScope;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "run-task",
    mixinStandardHelpOptions = true,
    description = "Run orchestration task defined in task.xml"
)
public class CliRunTaskCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliRunTaskCommand.class);

    @CommandLine.Parameters(index = "0", description = "Path to task.xml orchestration task file")
    String file;

    @CommandLine.Option(names = {"-i", "--input"}, description = "Input parameters (JSON)")
    String input;

    @CommandLine.Option(names = {"-f", "--flags"}, description = "Enable feature flags (e.g.: -f verbose,dry-run)")
    String flags;

    @CommandLine.Option(names = {"-if", "--input-file"}, description = "Input parameters file path")
    String inputFile;

    @CommandLine.Option(
        names = "-P",
        description = "Dynamic parameter (format: -Pname=value)",
        paramLabel = "KEY=VALUE"
    )
    Map<String, String> dynamicParams = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Integer call() throws Exception {
        IResource resource = ResourceHelper.resolveRelativePathResource(file);

        ITaskFlowManager taskFlowManager = BeanContainer.getBeanByType(ITaskFlowManager.class);
        ITask task = taskFlowManager.parseTask(resource);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, new ServiceContextImpl());
        Set<String> tagSet = StringHelper.parseCsvSet(flags);
        if (tagSet != null)
            taskRt.setTagSet(tagSet);

        if (input != null) {
            Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(null, input);
            taskRt.getEvalScope().setLocalValues(map);
        }
        if (inputFile != null) {
            IResource inputResource = ResourceHelper.resolveRelativePathResource(inputFile);
            Map<String, Object> map = (Map<String, Object>) JsonTool.parseBeanFromResource(inputResource);
            taskRt.getEvalScope().setLocalValues(map);
        }

        if (dynamicParams != null) {
            LOG.info("nop.cli.run-task-params:{}", dynamicParams);
            dynamicParams.forEach(taskRt::setInput);
        }

        return BeanScopeContext.runWithNewScope(IBeanScope.SCOPE_TASK, () -> {
            Object result = task.execute(taskRt).syncGetResult();
            if (result instanceof Integer)
                return (Integer) result;
            return 0;
        });
    }
}
