package io.nop.kernel.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate DSL model files using registered loaders"
)
public class KernelCliValidateCommand implements Callable<Integer> {

    static final Logger LOG = LoggerFactory.getLogger(KernelCliValidateCommand.class);

    @CommandLine.Parameters(description = "Model file names to validate", arity = "1..*")
    List<String> inputFiles;

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Show detailed validation info")
    boolean verbose;

    @Override
    public Integer call() {
        int errorCount = 0;

        for (String inputFile : inputFiles) {
            IResource resource = ResourceHelper.resolveRelativePathResource(inputFile);
            String path = resource.getPath();

            try {
                Object model = ResourceComponentManager.instance().loadComponentModel(path);

                if (verbose) {
                    String fileType = StringHelper.fileType(path);
                    System.out.println("[OK] " + inputFile + " (type: " + fileType + ", model: " +
                            (model != null ? model.getClass().getSimpleName() : "null") + ")");
                } else {
                    System.out.println("[OK] " + inputFile);
                }
            } catch (Exception e) {
                errorCount++;
                System.err.println("[FAIL] " + inputFile);
                // 堆栈统一走日志框架，与 CodeGenTask 的整改方向一致，便于日志采集/级别控制；
                // 非 verbose 时也记录，避免丢失定位信息
                LOG.error("nop.cli.validate-fail:{}", inputFile, e);
            }
        }

        System.out.println();
        if (errorCount == 0) {
            System.out.println("Validation completed: All " + inputFiles.size() + " file(s) passed.");
            return 0;
        } else {
            System.out.println("Validation completed: " + errorCount + " of " + inputFiles.size() + " file(s) failed.");
            return 1;
        }
    }
}
