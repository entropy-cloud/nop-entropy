package io.nop.cli.commands;

import io.nop.commons.util.MavenDirHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.zip.ZipOptions;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "repackage",
        mixinStandardHelpOptions = true,
        description = "将外部文件和cli工具打包在一起"
)
public class CliRepackageCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-i", "--input"}, description = "输入目录")
    String inputDir;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件")
    File outputFile;

    @Override
    public Integer call() throws Exception {
        File jarFile = MavenDirHelper.getClassesDir(CliRepackageCommand.class);

        IFile tempDir = (IFile) ResourceHelper.getTempResource("cli");
        try {
            ResourceHelper.unzipToDir(new FileResource(jarFile), tempDir, new ZipOptions());
            File bootConfigFile = new File(inputDir, "bootstrap.yaml");
            if (bootConfigFile.exists()) {
                ResourceHelper.copy(new FileResource(bootConfigFile), tempDir.getResource("bootstrap.yaml"));
            }

            File appConfigFile = new File(inputDir, "application.yaml");
            if (appConfigFile.exists()) {
                ResourceHelper.copy(new FileResource(appConfigFile), tempDir.getResource("application.yaml"));
            }

            File classesDir = new File(inputDir, "classes");
            if (classesDir.exists()) {
                ResourceHelper.copyDir(new FileResource(classesDir), tempDir);
            }

            ZipOptions options = new ZipOptions();
            options.setJarFile(true);
            ResourceHelper.zipDir(tempDir, new FileResource(outputFile), options);
        } finally {
            ResourceHelper.deleteAll(tempDir);
        }
        return 0;
    }
}
