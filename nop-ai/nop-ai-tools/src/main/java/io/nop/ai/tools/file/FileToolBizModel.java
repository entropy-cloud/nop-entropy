package io.nop.ai.tools.file;

import io.nop.ai.core.file.FileContent;
import io.nop.ai.core.file.FileContents;
import io.nop.ai.core.file.IFileOperator;
import io.nop.ai.core.file.LocalFileOperator;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@BizModel("FileTool")
public class FileToolBizModel {

    private File baseDir;
    private int defaultMaxLengthPerFile = 8192;

    @InjectValue("@cfg:nop.ai.file-tool.base-dir|/nop/projects")
    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    @InjectValue("@cfg:nop.ai.file-tool.default-max-length-per-file|8192")
    public void setDefaultMaxLengthPerFile(int defaultMaxLengthPerFile) {
        this.defaultMaxLengthPerFile = defaultMaxLengthPerFile;
    }

    @Description("读取文件")
    @BizQuery
    public String readFiles(
            @Name("projectName") String projectName,
            @Name("filePaths") List<String> filePaths,
            @Name("maxLengthPerFile") @Optional int maxLengthPerFile,
            @Name("maxTotalLength") @Optional int maxTotalLength) {
        IFileOperator fileOperator = getFileOperator(projectName);
        if (maxTotalLength <= 0)
            maxTotalLength = this.defaultMaxLengthPerFile;

        FileContents files = fileOperator.readFileContents(filePaths, maxLengthPerFile);
        if (maxTotalLength > 0)
            files = files.limitTotalLength(maxTotalLength);
        return files.toNode().xml();
    }

    @Description("读取文件的一部分")
    @BizQuery
    public String readFilePart(@Name("projectName") String projectName,
                               @Name("filePath") String filePath,
                               @Name("offset") int offset,
                               @Name("limit") int limit) {
        IFileOperator fileOperator = getFileOperator(projectName);
        return fileOperator.readFileContent(filePath, offset, limit).toNode().xml();
    }

    @Description("保存文件")
    @BizMutation
    public void saveFile(@Name("projectName") String projectName, @Name("filePath") String filePath, @Name("text") String text) {
        IFileOperator operator = getFileOperator(projectName);
        operator.writeFileContent(new FileContent(filePath, text));
    }

    @Description("保存多个文件。fileContents参数必须是XML格式的多文件内容表达。<files><file path='string'>!<[CDATA[ file-content-string ]]></file></files>")
    @BizMutation
    public void saveFiles(@Name("projectName") String projectName, @Name("String") String fileContents) {
        IFileOperator operator = getFileOperator(projectName);
        XNode node = XNode.parse(fileContents);
        FileContents contents = FileContents.fromNode(node);
        operator.writeFileContents(contents, true);
    }

    @Description("基于Nop平台中XDef元模型，使用Delta合并算法合并文件")
    @BizMutation
    public void mergeFile(@Name("projectName") String projectName, @Name("filePath") String filePath, @Name("text") String text) {
        IFileOperator operator = getFileOperator(projectName);
        operator.mergeFile(filePath, text);
    }


    @Description("使用glob模式搜索文件")
    @BizQuery
    public List<String> glob(
            @Name("projectName") String projectName,
            @Name("directory") String directory,
            @Name("pattern") String pattern,
            @Name("maxFileCount") Integer maxFileCount
    ) {
        int maxCount = maxFileCount != null ? maxFileCount : 0;

        IFileOperator fileOperator = getFileOperator(projectName);
        return fileOperator.findFilesByGlob(directory, pattern, maxCount);
    }

    @Description("使用glob模式查找文件，并在匹配的文件中搜索符合正则表达式的行")
    @BizQuery
    public String globGrep(
            @Name("projectName") String projectName,
            @Name("directory") String directory,
            @Name("globPattern") String globPattern,
            @Name("regex") String regex,
            @Optional @Name("ignoreCase") boolean ignoreCase,
            @Optional @Name("limitPerFile") Integer limitPerFile,
            @Optional @Name("totalLimit") Integer totalLimit) {
        IFileOperator fileOperator = getFileOperator(projectName);
        int perFile = limitPerFile != null ? limitPerFile : 0;
        int ttlLimit = totalLimit != null ? totalLimit : 0;

        List<IFileOperator.GrepResult> results = fileOperator.globGrep(directory, globPattern, regex, ignoreCase, perFile, ttlLimit);
        return convertToGrepStrings(results);
    }

    @Description("在文件中搜索匹配正则表达式的行")
    @BizQuery
    public String grep(
            @Name("projectName") String projectName,
            @Name("filePath") String filePath,
            @Name("regex") String regex,
            @Optional @Name("ignoreCase") boolean ignoreCase,
            @Optional @Name("limitPerFile") Integer limit) {
        IFileOperator fileOperator = getFileOperator(projectName);
        int perFile = limit != null ? limit : 50;

        List<IFileOperator.GrepResult> results = fileOperator.grep(filePath, regex, ignoreCase,
                perFile);
        return convertToGrepStrings(results);
    }

    @Description("在多个文件中搜索匹配正则表达式的行")
    @BizQuery
    public String grepFiles(
            @Name("projectName") String projectName,
            @Name("filePaths") List<String> filePaths,
            @Name("regex") String regex,
            @Optional @Name("ignoreCase") boolean ignoreCase,
            @Optional @Name("limitPerFile") Integer limitPerFile,
            @Optional @Name("totalLimit") Integer totalLimit) {
        IFileOperator fileOperator = getFileOperator(projectName);
        int perFile = limitPerFile != null ? limitPerFile : 0;
        int ttlLimit = totalLimit != null ? totalLimit : 0;

        List<IFileOperator.GrepResult> results = fileOperator.grepFiles(filePaths, regex, ignoreCase, perFile, ttlLimit);
        return convertToGrepStrings(results);
    }

    private String convertToGrepStrings(List<IFileOperator.GrepResult> results) {
        return results.stream()
                .map(IFileOperator.GrepResult::toString)
                .collect(Collectors.joining("\n"));
    }

    protected IFileOperator getFileOperator(String projectName) {
        File file = getProjectDir(projectName);
        return new LocalFileOperator(file);
    }

    protected File getProjectDir(String projectName) {
        String dirName = StringHelper.fileName(projectName);
        if (!StringHelper.isValidFileName(dirName))
            throw new IllegalArgumentException("projectName must be valid file directory name:" + projectName);
        return new File(baseDir, dirName);
    }
}