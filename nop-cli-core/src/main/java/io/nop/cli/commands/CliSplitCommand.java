package io.nop.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "split",
        mixinStandardHelpOptions = true,
        description = "切分文件为多个部分，保存到目标目录中"
)
public class CliSplitCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "文件名")
    String inputFile;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出目录", required = true)
    File outputDir;

    @CommandLine.Option(names = {"-l", "--level"}, description = "拆分层次")
    Integer level;

    @Override
    public Integer call() {
        IResource inputResource = ResourceHelper.resolveRelativePathResource(inputFile);
        String fileExt = StringHelper.fileExt(inputFile);
        int depth = level == null ? 1 : level;

        if (fileExt.equals("md")) {
            MarkdownDocument doc = MarkdownTool.instance().parseFromResource(inputResource);
            doc.resetLevel();
            doc.normalizeSectionNo();
            doc.splitToDir(outputDir, depth, null);
        } else {
            throw new IllegalArgumentException("unsupported file format:" + fileExt);
        }

        return 0;
    }
}