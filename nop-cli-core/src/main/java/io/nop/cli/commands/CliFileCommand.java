package io.nop.cli.commands;

import io.nop.ai.code_analyzer.git.GitIgnoreFile;
import io.nop.ai.coder.file.FileContents;
import io.nop.ai.coder.file.IFileOperator;
import io.nop.ai.coder.file.LocalFileOperator;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.path.PathTreeNode;
import io.nop.core.resource.path.ResourceToPathTreeBuilder;
import picocli.CommandLine;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@CommandLine.Command(
        name = "file",
        mixinStandardHelpOptions = true,
        description = "文件操作命令，支持读取和写入多个文件",
        subcommands = {CommandLine.HelpCommand.class}
)
public class CliFileCommand implements Callable<Integer> {

    @CommandLine.Parameters(
            index = "0",
            description = "操作类型: read|write|path-tree|find",
            arity = "1"
    )
    String operation;

    @CommandLine.Option(names = {"-b", "--base-dir"}, description = "基础目录", defaultValue = ".")
    File baseDir;

    @CommandLine.Option(
            names = {"-p", "--pattern"},
            description = "文件匹配模式(Ant风格路径)，仅read操作需要"
    )
    String pattern;

    @CommandLine.Option(
            names = {"-r", "--regex"},
            description = "正则匹配模式，在find和path-tree时使用"
    )
    String regex;

    @CommandLine.Option(
            names = {"-d", "--dir"},
            description = "搜索目录，仅read操作需要",
            defaultValue = "/"
    )
    String searchDir;

    @CommandLine.Option(
            names = {"-mf", "--max-files"},
            description = "最大读取文件数量",
            defaultValue = "10"
    )
    int maxFiles;

    @CommandLine.Option(
            names = {"-ml", "--max-length-per-file"},
            description = "每个文件读取的最大字符数",
            defaultValue = "2000"
    )
    int maxLengthPerFile;


    @CommandLine.Option(
            names = {"-l", "--max-depth"},
            description = "最大遍历目录深度",
            defaultValue = "-1"
    )
    int maxDepth;

    @CommandLine.Option(
            names = {"-c", "--content"},
            description = "要写入的内容(支持多文件格式)，仅write操作需要"
    )
    String content;

    @CommandLine.Option(
            names = {"-i", "--input"},
            description = "从输入文件中读取内容进行写入，仅write操作需要"
    )
    File inputFile;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "输出目标(文件或目录路径)",
            defaultValue = ""
    )
    String outputPath;

    @CommandLine.Option(
            names = {"-O", "--overwrite"},
            description = "是否覆盖已存在文件"
    )
    boolean overwrite;

    private IFileOperator fileOperator;

    @Override
    public Integer call() {
        fileOperator = new LocalFileOperator(baseDir);

        try {
            switch (operation.toLowerCase()) {
                case "read":
                    return handleRead();
                case "write":
                    return handleWrite();
                case "path-tree":
                    return handlePathTree();
                case "find":
                    return handleFind();
                default:
                    System.err.println("Error: Unknown operation. Must be 'read' or 'write'");
                    return 1;
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private Integer handleRead() {
        if (pattern == null) {
            throw new IllegalArgumentException("--pattern is required for read operation");
        }

        FileContents contents = fileOperator.readFileContentsByAntPath(searchDir, pattern, maxFiles, maxLengthPerFile);
        String xml = contents.toNode().xml();

        if (!outputPath.isEmpty()) {
            File outputFile = new File(outputPath);
            FileHelper.writeText(outputFile, xml, null);
            System.out.println("Read results saved to: " + outputPath);
        } else {
            System.out.println(xml);
        }
        return 0;
    }

    private Integer handleWrite() {
        if (content == null) {
            throw new IllegalArgumentException("--content is required for write operation");
        }

        FileContents contents;
        if (inputFile != null) {
            // 从输入文件读取内容
            String fileContent = FileHelper.readText(inputFile, null);
            contents = FileContents.fromText(fileContent);
        } else {
            // 使用直接提供的内容
            contents = FileContents.fromText(content);
        }

        if (outputPath.isEmpty()) {
            // 如果没有指定输出路径，使用内容中的第一个文件名
            if (contents.getFiles().isEmpty()) {
                throw new IllegalArgumentException("No file content provided");
            }
            fileOperator.writeFileContents(contents, overwrite);
            System.out.println("Files written successfully");
        } else {
            File outputFile = new File(outputPath);
            if (outputFile.isDirectory()) {
                // 如果输出目标是目录，写入多个文件
                fileOperator.writeFileContents(contents, overwrite);
                System.out.println("Files written to directory: " + outputPath);
            } else {
                // 如果输出目标是文件，只写入第一个文件内容
                if (!contents.getFiles().isEmpty()) {
                    FileHelper.writeText(outputFile, contents.getFiles().get(0).getContent(), null);
                    System.out.println("File written to: " + outputPath);
                }
            }
        }
        return 0;
    }

    private Integer handlePathTree() {
        IResource resource = fileOperator.getResource(searchDir);
        Pattern regexPattern = regex == null ? null : Pattern.compile(regex);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        GitIgnoreFile ignoreFile = getGitIgnoreFile();

        PathTreeNode pathTree = ResourceToPathTreeBuilder.buildFromResource(resource, maxDepth, res -> {
            if (res.isDirectory())
                return true;

            if (!ignoreFile.isEmpty() && ignoreFile.test(res))
                return false;

            if (regexPattern != null)
                return regexPattern.matcher(res.getPath()).find();

            if (pattern != null) {
                String relativePath = res.getPath().substring(resource.getPath().length() + 1);
                return pathMatcher.match(pattern, relativePath);
            }
            return true;
        });

        pathTree.removeEmptyDir();

        String text = pathTree.buildTreeString();

        if (!outputPath.isEmpty()) {
            File outputFile = new File(outputPath);
            FileHelper.writeText(outputFile, text, null);
            System.out.println("Path Tree saved to: " + outputPath);
        } else {
            System.out.println(text);
        }

        return 0;
    }

    private Integer handleFind() {
        Pattern regexPattern = regex == null ? null : Pattern.compile(regex);
        AntPathMatcher pathMatcher = new AntPathMatcher();

        GitIgnoreFile ignoreFile = getGitIgnoreFile();

        List<String> paths = fileOperator.findFilesByFilter(searchDir, path -> {
            IResource res = fileOperator.getResource(path);

            if (!ignoreFile.isEmpty() && ignoreFile.test(res))
                return false;

            if (regexPattern != null)
                return regexPattern.matcher(path).find();

            if (pattern != null) {
                return pathMatcher.match(pattern, path);
            }
            return true;
        });

        String text = StringHelper.join(paths, "\n");

        if (!outputPath.isEmpty()) {
            File outputFile = new File(outputPath);
            FileHelper.writeText(outputFile, text, null);
            System.out.println("Paths saved to: " + outputPath);
        } else {
            System.out.println(text);
        }

        return 0;
    }

    GitIgnoreFile getGitIgnoreFile() {
        GitIgnoreFile file = GitIgnoreFile.create(new FileResource(baseDir));
        return file;
    }
}