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
        description = "在DSL模型的各种文件格式（XML/JSON/YAML/XLSX等）之间进行转换"
)
public class CliConvertCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "模型文件名", index = "0", arity = "1..*")
    List<String> inputFiles;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件", required = true)
    File outputFile;

    @CommandLine.Option(names = {"-a", "--attachment-dir"}, description = "附件目录")
    File attachmentDir;

    @Override
    public Integer call() {

        // 解析输入文件
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