package io.nop.code.service.entity;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.PageBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeFileBiz;
import io.nop.code.core.model.*;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.FileOutlineDTO;
import io.nop.code.service.api.dto.FileTreeNode;
import io.nop.code.service.api.dto.SymbolInfoDTO;
@BizModel("NopCodeFile")
public class NopCodeFileBizModel extends CrudBizModel<NopCodeFile> implements INopCodeFileBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeFileBizModel() {
        setEntityName(NopCodeFile.class.getName());
    }

    @BizQuery
    @Auth(permissions = "code-query")
    public CodeFileAnalysisResult getByPath(
            @Name("filePath") String filePath,
            @Name("indexId") String indexId) {
        return codeIndexService.getFile(indexId, filePath);
    }

    @BizQuery
    @Auth(permissions = "code-query")
    public PageBean<CodeFileAnalysisResult> findPage_files(
            @Name("indexId") String indexId,
            @Name("packageName") @Optional String packageName,
            @Name("offset") @Optional long offset,
            @Name("limit") @Optional int limit) {
        return codeIndexService.findFilesPage(indexId, packageName, offset, limit > 0 ? limit : 20);
    }

    @BizQuery
    @Auth(permissions = "code-query")
    public List<FileTreeNode> fileTree(@Name("indexId") String indexId) {
        return codeIndexService.getFileTree(indexId);
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) {
        return file.getSymbols();
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    public List<CodeSymbol> types(@ContextSource CodeFileAnalysisResult file) {
        return file.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                        || s.getKind() == CodeSymbolKind.INTERFACE
                        || s.getKind() == CodeSymbolKind.ENUM
                        || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
                .collect(Collectors.toList());
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    @Auth(permissions = "code-source-read")
    public String sourceCode(@ContextSource CodeFileAnalysisResult file) {
        return file.getSourceCode();
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    public FileOutlineDTO outline(@ContextSource CodeFileAnalysisResult file) {
        FileOutlineDTO outline = new FileOutlineDTO();
        outline.setFilePath(file.getFilePath());
        outline.setPackageName(file.getPackageName());
        outline.setImports(file.getImports());
        outline.setLineCount(file.getLineCount());
        outline.setTypes(file.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                        || s.getKind() == CodeSymbolKind.INTERFACE
                        || s.getKind() == CodeSymbolKind.ENUM
                        || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
                .map(this::toSymbolInfo)
                .collect(Collectors.toList()));
        return outline;
    }

    private SymbolInfoDTO toSymbolInfo(CodeSymbol s) {
        SymbolInfoDTO info = new SymbolInfoDTO();
        info.setName(s.getName());
        info.setQualifiedName(s.getQualifiedName());
        info.setKind(s.getKind() != null ? s.getKind().name() : null);
        info.setAccessModifier(s.getAccessModifier() != null ? s.getAccessModifier().name() : null);
        return info;
    }
}
