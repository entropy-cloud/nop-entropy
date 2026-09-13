package io.nop.ai.tools.file;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;

import java.util.List;

/**
 * FileTool 能力契约。注解在接口与实现类双侧声明（平台 ICrudBiz 先例）；
 * 运行时生效面在实现类方法上。
 */
public interface IFileToolBiz {

    @BizQuery
    String readFiles(@Name("projectName") String projectName,
                     @Name("filePaths") List<String> filePaths,
                     @Name("maxLengthPerFile") @Optional int maxLengthPerFile,
                     @Name("maxTotalLength") @Optional int maxTotalLength,
                     IServiceContext context);

    @BizQuery
    String readFilePart(@Name("projectName") String projectName,
                        @Name("filePath") String filePath,
                        @Name("offset") int offset,
                        @Name("limit") int limit,
                        IServiceContext context);

    @BizMutation
    void saveFile(@Name("projectName") String projectName,
                  @Name("filePath") String filePath,
                  @Name("text") String text,
                  IServiceContext context);

    @BizMutation
    void saveFiles(@Name("projectName") String projectName,
                   @Name("fileContents") String fileContents,
                   IServiceContext context);

    @BizMutation
    void mergeFile(@Name("projectName") String projectName,
                   @Name("filePath") String filePath,
                   @Name("text") String text,
                   IServiceContext context);

    @BizQuery
    List<String> glob(@Name("projectName") String projectName,
                      @Name("directory") String directory,
                      @Name("pattern") String pattern,
                      @Name("maxFileCount") Integer maxFileCount,
                      IServiceContext context);

    @BizQuery
    String globGrep(@Name("projectName") String projectName,
                    @Name("directory") String directory,
                    @Name("globPattern") String globPattern,
                    @Name("regex") String regex,
                    @Optional @Name("ignoreCase") boolean ignoreCase,
                    @Optional @Name("limitPerFile") Integer limitPerFile,
                    @Optional @Name("totalLimit") Integer totalLimit,
                    IServiceContext context);

    @BizQuery
    String grep(@Name("projectName") String projectName,
                @Name("filePath") String filePath,
                @Name("regex") String regex,
                @Optional @Name("ignoreCase") boolean ignoreCase,
                @Optional @Name("limitPerFile") Integer limit,
                IServiceContext context);

    @BizQuery
    String grepFiles(@Name("projectName") String projectName,
                     @Name("filePaths") List<String> filePaths,
                     @Name("regex") String regex,
                     @Optional @Name("ignoreCase") boolean ignoreCase,
                     @Optional @Name("limitPerFile") Integer limitPerFile,
                     @Optional @Name("totalLimit") Integer totalLimit,
                     IServiceContext context);

    @BizQuery
    String loadDslSchema(@Name("projectName") String projectName,
                         @Name("schemaPath") String schemaPath,
                         IServiceContext context);

    @BizQuery
    String loadDslSchemaForFileType(@Name("projectName") String projectName,
                                    @Name("fileType") String fileType,
                                    IServiceContext context);

    @BizQuery
    String loadDslFile(@Name("projectName") String projectName,
                       @Name("filePath") String filePath,
                       @Optional @Name("toFileType") String toFileType,
                       IServiceContext context);

    @BizMutation
    void saveDslFile(@Name("projectName") String projectName,
                     @Name("filePath") String filePath,
                     @Optional @Name("fromFileType") String fromFileType,
                     @Name("content") String content,
                     IServiceContext context);
}
