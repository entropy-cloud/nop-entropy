package io.nop.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.report.core.engine.renderer.SimpleHtmlSplitter;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "split",
    mixinStandardHelpOptions = true,
    description = "Split a file into multiple parts and save them to target directory"
)
public class CliSplitCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "Input file name")
    String inputFile;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory", required = true)
    File outputDir;

    @CommandLine.Option(names = {"-l", "--level"}, description = "Split depth level")
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
        } else if (fileExt.equals("shtml")) {
            new SimpleHtmlSplitter(false).split(inputResource, outputDir);
        } else {
            throw new IllegalArgumentException("unsupported file format:" + fileExt);
        }

        return 0;
    }
}