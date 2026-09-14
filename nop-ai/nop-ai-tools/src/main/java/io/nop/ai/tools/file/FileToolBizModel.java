package io.nop.ai.tools.file;

import io.nop.ai.tools.xdsl.DslToolImpl;
import io.nop.ai.tools.xdsl.IDslTool;
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
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.ai.core.NopAiCoreErrors.ARG_VALUE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_PROJECT_NAME;

/**
 * File tool operations. Uses {@link io.nop.ai.core.file.IFileOperator} (deprecated, forRemoval).
 * Migration target: {@link io.nop.ai.toolkit.fs.IToolFileSystem} from nop-ai-toolkit.
 * <p>
 * <b>P2-MA1-012 ruling (2026-07-31) — retained with documentation (no migration):</b>
 * the two file-system abstractions are kept side by side (IFileOperator = base-dir
 * scoped resource ops; IToolFileSystem = sandboxed executor FS). Migration
 * preconditions (method-surface reconciliation, semantic alignment, consumer
 * migration) and the boundary contract are documented in
 * {@code ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md}.
 */
@BizModel("FileTool")
@SuppressWarnings("deprecation")
public class FileToolBizModel implements IFileToolBiz {

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
    @Auth(permissions = "FileTool:read")
    @Override
    public String readFiles(
            @Name("projectName") String projectName,
            @Name("filePaths") List<String> filePaths,
            @Name("maxLengthPerFile") @Optional int maxLengthPerFile,
            @Name("maxTotalLength") @Optional int maxTotalLength,
            IServiceContext context) {
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
    @Auth(permissions = "FileTool:read")
    @Override
    public String readFilePart(@Name("projectName") String projectName,
                               @Name("filePath") String filePath,
                               @Name("offset") int offset,
                               @Name("limit") int limit,
                               IServiceContext context) {
        IFileOperator fileOperator = getFileOperator(projectName);
        return fileOperator.readFileContent(filePath, offset, limit).toNode().xml();
    }

    @Description("保存文件")
    @BizMutation
    @Auth(permissions = "FileTool:write")
    @Override
    public void saveFile(@Name("projectName") String projectName, @Name("filePath") String filePath, @Name("text") String text,
                         IServiceContext context) {
        IFileOperator operator = getFileOperator(projectName);
        operator.writeFileContent(new FileContent(filePath, text));
    }

    @Description("保存多个文件。fileContents参数必须是XML格式的多文件内容表达。<files><file path='string'>!<[CDATA[ file-content-string ]]></file></files>")
    @BizMutation
    @Auth(permissions = "FileTool:write")
    @Override
    public void saveFiles(@Name("projectName") String projectName, @Name("fileContents") String fileContents,
                          IServiceContext context) {
        IFileOperator operator = getFileOperator(projectName);
        XNode node = XNode.parse(fileContents);
        FileContents contents = FileContents.fromNode(node);
        operator.writeFileContents(contents, true);
    }

    @Description("基于Nop平台中XDef元模型，使用Delta合并算法合并文件")
    @BizMutation
    @Auth(permissions = "FileTool:write")
    @Override
    public void mergeFile(@Name("projectName") String projectName, @Name("filePath") String filePath, @Name("text") String text,
                          IServiceContext context) {
        IFileOperator operator = getFileOperator(projectName);
        operator.mergeFile(filePath, text);
    }

    @Description("使用glob模式搜索文件")
    @BizQuery
    @Auth(permissions = "FileTool:search")
    @Override
    public List<String> glob(
            @Name("projectName") String projectName,
            @Name("directory") String directory,
            @Name("pattern") String pattern,
            @Name("maxFileCount") Integer maxFileCount,
            IServiceContext context
    ) {
        int maxCount = maxFileCount != null ? maxFileCount : 0;

        IFileOperator fileOperator = getFileOperator(projectName);
        return fileOperator.findFilesByGlob(directory, pattern, maxCount);
    }

    @Description("使用glob模式查找文件，并在匹配的文件中搜索符合正则表达式的行")
    @BizQuery
    @Auth(permissions = "FileTool:search")
    @Override
    public String globGrep(
            @Name("projectName") String projectName,
            @Name("directory") String directory,
            @Name("globPattern") String globPattern,
            @Name("regex") String regex,
            @Optional @Name("ignoreCase") boolean ignoreCase,
            @Optional @Name("limitPerFile") Integer limitPerFile,
            @Optional @Name("totalLimit") Integer totalLimit,
            IServiceContext context) {
        IFileOperator fileOperator = getFileOperator(projectName);
        int perFile = limitPerFile != null ? limitPerFile : 0;
        int ttlLimit = totalLimit != null ? totalLimit : 0;

        List<IFileOperator.GrepResult> results = fileOperator.globGrep(directory, globPattern, regex, ignoreCase, perFile, ttlLimit);
        return convertToGrepStrings(results);
    }

    @Description("在文件中搜索匹配正则表达式的行")
    @BizQuery
    @Auth(permissions = "FileTool:search")
    @Override
    public String grep(
            @Name("projectName") String projectName,
            @Name("filePath") String filePath,
            @Name("regex") String regex,
            @Optional @Name("ignoreCase") boolean ignoreCase,
            @Optional @Name("limitPerFile") Integer limit,
            IServiceContext context) {
        IFileOperator fileOperator = getFileOperator(projectName);
        int perFile = limit != null ? limit : 50;

        List<IFileOperator.GrepResult> results = fileOperator.grep(filePath, regex, ignoreCase,
                perFile);
        return convertToGrepStrings(results);
    }

    @Description("在多个文件中搜索匹配正则表达式的行")
    @BizQuery
    @Auth(permissions = "FileTool:search")
    @Override
    public String grepFiles(
            @Name("projectName") String projectName,
            @Name("filePaths") List<String> filePaths,
            @Name("regex") String regex,
            @Optional @Name("ignoreCase") boolean ignoreCase,
            @Optional @Name("limitPerFile") Integer limitPerFile,
            @Optional @Name("totalLimit") Integer totalLimit,
            IServiceContext context) {
        IFileOperator fileOperator = getFileOperator(projectName);
        int perFile = limitPerFile != null ? limitPerFile : 0;
        int ttlLimit = totalLimit != null ? totalLimit : 0;

        List<IFileOperator.GrepResult> results = fileOperator.grepFiles(filePaths, regex, ignoreCase, perFile, ttlLimit);
        return convertToGrepStrings(results);
    }

    @Description("加载DSL文件的元模型定义")
    @BizQuery
    @Auth(permissions = "FileTool:read")
    @Override
    public String loadDslSchema(
            @Name("projectName") String projectName,
            @Name("schemaPath") String schemaPath,
            IServiceContext context) {
        IDslTool dslTool = getDslTool(projectName);
        return dslTool.loadDslSchema(schemaPath);
    }

    @Description("根据文件类型加载对应的DSL元模型定义")
    @BizQuery
    @Auth(permissions = "FileTool:read")
    @Override
    public String loadDslSchemaForFileType(
            @Name("projectName") String projectName,
            @Name("fileType") String fileType,
            IServiceContext context) {
        IDslTool dslTool = getDslTool(projectName);
        return dslTool.loadDslSchemaForFileType(fileType);
    }

    @Description("加载DSL文件并转换为指定格式")
    @BizQuery
    @Auth(permissions = "FileTool:read")
    @Override
    public String loadDslFile(
            @Name("projectName") String projectName,
            @Name("filePath") String filePath,
            @Optional @Name("toFileType") String toFileType,
            IServiceContext context) {
        IDslTool dslTool = getDslTool(projectName);
        return dslTool.loadDslFile(filePath, toFileType);
    }

    @Description("保存DSL文件，支持格式转换")
    @BizMutation
    @Auth(permissions = "FileTool:write")
    @Override
    public void saveDslFile(
            @Name("projectName") String projectName,
            @Name("filePath") String filePath,
            @Optional @Name("fromFileType") String fromFileType,
            @Name("content") String content,
            IServiceContext context) {
        IDslTool dslTool = getDslTool(projectName);
        dslTool.saveDslFile(filePath, fromFileType, content);
    }

    protected IDslTool getDslTool(String projectName) {
        // M5-P1 round-1 audit finding 3, decision A: IDslTool/DslToolImpl sunk
        // down from nop-ai-coder to nop-ai-tools (io.nop.ai.tools.xdsl) — this
        // module no longer depends on the nop-ai-coder heavyweight chain.
        return new DslToolImpl(getFileOperator(projectName));
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
        // M6-P1 (round-2 audit): fail-closed projectName validation, aligned
        // with AiToolsHelper.requireValidSessionId. StringHelper.fileName()
        // truncates at '/' and returns the last part verbatim, so ".." passes
        // through untouched and isValidFileName (control-char / platform
        // invalid-char check only) does not reject it — new File(baseDir, "..")
        // would escape the sandbox one level up (default /nop/projects -> /nop).
        // The raw input must be rejected BEFORE any truncation.
        if (projectName == null || projectName.indexOf('/') >= 0 || projectName.indexOf('\\') >= 0) {
            throw new NopException(ERR_AI_TOOLS_INVALID_PROJECT_NAME)
                    .param(ARG_VALUE, projectName);
        }
        String dirName = StringHelper.fileName(projectName);
        if (StringHelper.isEmpty(dirName) || ".".equals(dirName) || "..".equals(dirName)
                || !StringHelper.isValidFileName(dirName))
            throw new NopException(ERR_AI_TOOLS_INVALID_PROJECT_NAME)
                    .param(ARG_VALUE, projectName);
        return new File(baseDir, dirName);
    }
}
