package io.nop.kernel.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "convert",
        mixinStandardHelpOptions = true,
        description = "Convert between DSL model file formats (XML/JSON/YAML/XLSX etc.)"
)
public class KernelCliConvertCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "Model file name")
    String inputFile;

    @CommandLine.Option(names = {"-p", "--phase"}, description = "Resolve Phase")
    IResourceDslNodeLoader.ResolvePhase phase;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file", required = true)
    File outputFile;

    @CommandLine.Option(names = {"-a", "--attachment-dir"}, description = "Attachment directory")
    File attachmentDir;

    @Override
    public Integer call() {

        // Parse input files
        IResource inputResource = ResourceHelper.resolveRelativePathResource(inputFile);
        String fileType = StringHelper.fileType(inputResource.getPath());
        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(fileType);
        String toFileType = StringHelper.fileType(outputFile.getPath());

        IResource outputResource = new FileResource(outputFile);

        ComponentModelConfig.LoaderConfig fromLoader = config.getLoader(fileType);
        ComponentModelConfig.LoaderConfig toLoader = config.getLoader(toFileType);

        if (fromLoader == null || toLoader == null)
            throw new IllegalArgumentException("unsupported transformation from " + fileType + " to " + toFileType);

        if (fromLoader.getDslNodeLoader() != null && toLoader.getDslNodeSaver() != null) {
            XNode node = fromLoader.getDslNodeLoader().loadDslNodeFromResource(inputResource, phase);
            toLoader.getDslNodeSaver().saveDslNodeToResource(outputResource, node);
        } else {
            Object bean = fromLoader.getLoader().loadObjectFromResource(inputResource);
            toLoader.getSaver().saveObjectToResource(outputResource, bean);
        }
        return 0;
    }
}