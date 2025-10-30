package io.nop.cli.commands;

import io.nop.converter.utils.DocConvertHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.ooxml.docx.utils.DocxHelper;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "convert",
    mixinStandardHelpOptions = true,
    description = "Convert between DSL model file formats (XML/JSON/YAML/XLSX etc.)"
)
public class CliConvertCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "Model file names", index = "0", arity = "1..*")
    List<String> inputFiles;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file", required = true)
    File outputFile;

    @CommandLine.Option(names = {"-a", "--attachment-dir"}, description = "Attachment directory")
    File attachmentDir;

    @Override
    public Integer call() {

    // Parse input files
        List<IResource> resources = new ArrayList<>();
        for (String inputFile : inputFiles) {
            IResource inputResource = ResourceHelper.resolveRelativePathResource(inputFile);
            resources.add(inputResource);

            if (attachmentDir != null) {
                extractAttachment(inputResource);
            }
        }

        IResource outputResource = new FileResource(outputFile);

        DocConvertHelper.mergeAndConvertResources(resources, outputResource);

        return 0;
    }

    void extractAttachment(IResource resource) {
        String outputFileName = outputFile.getName();
        if (resource.getName().endsWith(".docx")) {
            if (outputFileName.endsWith(".md")) {
                DocxHelper.extractImagesToDir(resource, attachmentDir);
            }
        }
    }
}