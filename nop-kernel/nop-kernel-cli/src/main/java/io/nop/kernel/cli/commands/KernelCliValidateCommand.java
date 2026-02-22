package io.nop.kernel.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate DSL model files using registered loaders"
)
public class KernelCliValidateCommand implements Callable<Integer> {

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
                System.err.println("       " + e.getMessage());

                if (verbose) {
                    e.printStackTrace();
                }
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
