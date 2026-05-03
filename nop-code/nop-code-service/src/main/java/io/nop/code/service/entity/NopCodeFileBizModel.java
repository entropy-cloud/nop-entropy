
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeFileBiz;
import io.nop.code.core.model.*;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.FileOutlineDTO;
import io.nop.code.service.api.dto.FileTreeNode;
import io.nop.code.service.api.dto.SymbolInfoDTO;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

@BizModel("NopCodeFile")
public class NopCodeFileBizModel extends CrudBizModel<NopCodeFile> implements INopCodeFileBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeFileBizModel() {
        setEntityName(NopCodeFile.class.getName());
    }

    @BizQuery
    public CodeFileAnalysisResult getByPath(
            @Name("filePath") String filePath,
            @Name("indexId") String indexId) {
        return codeIndexService.getFile(indexId, filePath);
    }

    @BizQuery
    public List<CodeFileAnalysisResult> findFiles(
            @Name("indexId") String indexId,
            @Name("packageName") String packageName,
            @Name("limit") int limit) {
        List<CodeFileAnalysisResult> files = codeIndexService.getFiles(indexId);
        if (packageName != null) {
            files = files.stream()
                    .filter(f -> packageName.equals(f.getPackageName()))
                    .collect(Collectors.toList());
        }
        if (limit > 0 && files.size() > limit) {
            return files.subList(0, limit);
        }
        return files;
    }

    @BizQuery
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
        info.setKind(s.getKind().name());
        info.setAccessModifier(s.getAccessModifier() != null ? s.getAccessModifier().name() : null);
        return info;
    }
}
