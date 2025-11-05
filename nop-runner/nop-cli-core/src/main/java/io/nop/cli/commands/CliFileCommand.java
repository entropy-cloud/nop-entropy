package io.nop.cli.commands;

import io.nop.ai.code_analyzer.git.GitIgnoreFile;
import io.nop.ai.core.file.FileContents;
import io.nop.ai.core.file.IFileOperator;
import io.nop.ai.core.file.LocalFileOperator;
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
    description = "File operations: read, write, path-tree, plain-path-tree, find",
    subcommands = {CommandLine.HelpCommand.class}
)
public class CliFileCommand implements Callable<Integer> {

    @CommandLine.Parameters(
        index = "0",
        description = "Operation type: read|write|path-tree|plain-path-tree|find",
        arity = "1"
    )
    String operation;

    @CommandLine.Option(names = {"-b", "--base-dir"}, description = "Base directory", defaultValue = ".")
    File baseDir;

    @CommandLine.Option(
        names = {"-p", "--pattern"},
        description = "File match pattern (Ant style path), required for read operation"
    )
    String pattern;

    @CommandLine.Option(
        names = {"-r", "--regex"},
        description = "Regex match pattern, used by find and path-tree operations"
    )
    String regex;

    @CommandLine.Option(
        names = {"-d", "--dir"},
        description = "Search directory (root), required for read operation",
        defaultValue = "/"
    )
    String searchDir;

    @CommandLine.Option(
        names = {"-mf", "--max-files"},
        description = "Maximum number of files to read",
        defaultValue = "10"
    )
    int maxFiles;

    @CommandLine.Option(
        names = {"-ml", "--max-length-per-file"},
        description = "Maximum characters to read per file",
        defaultValue = "2000"
    )
    int maxLengthPerFile;


    @CommandLine.Option(
        names = {"-l", "--max-depth"},
        description = "Max directory traversal depth (-1 for unlimited)",
        defaultValue = "-1"
    )
    int maxDepth;

    @CommandLine.Option(
        names = {"-c", "--content"},
        description = "Content to write (supports multi-file format). Required for write operation"
    )
    String content;

    @CommandLine.Option(
        names = {"-i", "--input"},
        description = "Read content from input file (write operation)"
    )
    File inputFile;

    @CommandLine.Option(
        names = {"-o", "--output"},
        description = "Output target (file or directory path)",
        defaultValue = ""
    )
    String outputPath;

    @CommandLine.Option(
        names = {"-O", "--overwrite"},
        description = "Overwrite existing files"
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
                case "plain-path-tree":
                    return handlePathTree("plain-path-tree".equalsIgnoreCase(operation));
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
            // Read content from input file
            String fileContent = FileHelper.readText(inputFile, null);
            contents = FileContents.fromText(fileContent);
        } else {
            // Use directly provided content
            contents = FileContents.fromText(content);
        }

        if (outputPath.isEmpty()) {
            // If no output path specified, use first file name from content bundle
            if (contents.getFiles().isEmpty()) {
                throw new IllegalArgumentException("No file content provided");
            }
            fileOperator.writeFileContents(contents, overwrite);
            System.out.println("Files written successfully");
        } else {
            File outputFile = new File(outputPath);
            if (outputFile.isDirectory()) {
                // If output target is a directory, write multiple files
                fileOperator.writeFileContents(contents, overwrite);
                System.out.println("Files written to directory: " + outputPath);
            } else {
                // If output target is a file, only write first file's content
                if (!contents.getFiles().isEmpty()) {
                    FileHelper.writeText(outputFile, contents.getFiles().get(0).getContent(), null);
                    System.out.println("File written to: " + outputPath);
                }
            }
        }
        return 0;
    }

    private Integer handlePathTree(boolean plain) {
        IResource resource = fileOperator.getResource(searchDir);
        Pattern regexPattern = regex == null ? null : Pattern.compile(regex);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        GitIgnoreFile ignoreFile = getGitIgnoreFile();

        PathTreeNode pathTree = ResourceToPathTreeBuilder.buildFromResource(resource, maxDepth, res -> {
            if (res.isDirectory())
                return true;

            String path = res.getPath().substring(resource.getPath().length() + 1);
            if (isNopIgnoredFile(path))
                return false;

            if (!ignoreFile.isEmpty() && ignoreFile.test(res))
                return false;

            if (regexPattern != null)
                return regexPattern.matcher(path).find();

            if (pattern != null) {
                return pathMatcher.match(pattern, path);
            }
            return true;
        });

        pathTree.removeEmptyDir();

        String text = plain ? pathTree.buildPlainTreeString() : pathTree.buildTreeString();

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

            if (isNopIgnoredFile(path))
                return false;

            if (!ignoreFile.isEmpty() && ignoreFile.test(res))
                return false;

            if (regexPattern != null)
                return regexPattern.matcher(path).find();

            if (pattern != null) {
                return pathMatcher.match(pattern, path);
            }
            return true;
        },0);

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

    boolean isNopIgnoredFile(String path) {
        if (path.startsWith(".git/"))
            return true;

        if (path.startsWith(".idea/"))
            return true;

        if (path.startsWith(".github/"))
            return true;

        if (path.startsWith("_dump"))
            return true;

        if (path.contains("/_gen/") || path.contains("/_"))
            return true;
        return false;
    }

    GitIgnoreFile getGitIgnoreFile() {
        GitIgnoreFile file = GitIgnoreFile.create(new FileResource(baseDir));
        return file;
    }
}