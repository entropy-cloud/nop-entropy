package io.nop.cli.commands;

import io.nop.converter.DocumentConverterManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "convert",
        mixinStandardHelpOptions = true,
        description = "在DSL模型的各种文件格式（XML/JSON/YAML/XLSX等）之间进行转换"
)
public class CliConvertCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "模型文件名", index = "0")
    String inputFile;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件", required = true)
    File outputFile;

    @Override
    public Integer call() {
        // 解析输入文件
        IResource inputResource = ResourceHelper.resolveRelativePathResource(inputFile);
        IResource outputResource = new FileResource(outputFile);

        DocumentConverterManager.instance().convertResource(inputResource, outputResource, true);

        return 0;
    }

}