
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeSymbolBiz;
import io.nop.code.core.model.CodeAnnotationUsage;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.flow.DeadCodeReport;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.AnnotationUsageDTO;
import io.nop.code.service.api.dto.CallHierarchyDTO;
import io.nop.code.service.api.dto.CodeSearchResultDTO;
import io.nop.code.service.api.dto.FileOutlineDTO;
import io.nop.code.service.api.dto.ModuleDigestDTO;
import io.nop.code.service.api.dto.PublicAPIDTO;
import io.nop.code.service.api.dto.ReferenceDTO;
import io.nop.code.service.api.dto.SymbolDTO;
import io.nop.code.service.api.dto.SymbolSourceDTO;
import io.nop.code.service.api.dto.TypeHierarchyDTO;
import io.nop.code.service.api.dto.TypeOutlineDTO;
import io.nop.code.service.NopCodeErrors;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@BizModel("NopCodeSymbol")
public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> implements INopCodeSymbolBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeSymbolBizModel() {
        setEntityName(NopCodeSymbol.class.getName());
    }

    @BizQuery
    public SymbolDTO getBySymbolId(@Name("id") String id, @Name("indexId") String indexId) {
        CodeSymbol symbol = codeIndexService.getSymbolById(indexId, id);
        return symbol != null ? SymbolDTO.fromCodeSymbol(symbol) : null;
    }

    @BizQuery
    public SymbolDTO findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        CodeSymbol symbol = codeIndexService.findSymbolByQualifiedName(indexId, qualifiedName);
        return symbol != null ? SymbolDTO.fromCodeSymbol(symbol) : null;
    }

    @BizQuery
    public PageBean<SymbolDTO> findPage_symbols(
            @Name("query") @Optional String query,
            @Name("kinds") @Optional List<String> kinds,
            @Name("packageName") @Optional String packageName,
            @Name("indexId") String indexId,
            @Name("offset") @Optional long offset,
            @Name("limit") @Optional int limit) {
        List<CodeSymbolKind> kindList = null;
        if (kinds != null) {
            kindList = kinds.stream()
                    .map(k -> {
                        try {
                            return Enum.valueOf(CodeSymbolKind.class, k);
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        PageBean<CodeSymbol> page = codeIndexService.findSymbolsPage(indexId, query, kindList, packageName,
                offset, limit > 0 ? limit : 20);

        PageBean<SymbolDTO> result = new PageBean<>();
        result.setTotal(page.getTotal());
        result.setOffset(page.getOffset());
        result.setLimit(page.getLimit());
        result.setItems(page.getItems().stream()
                .map(SymbolDTO::fromCodeSymbol)
                .collect(Collectors.toList()));
        return result;
    }

    @BizLoader(forType = SymbolDTO.class)
    public List<AnnotationUsageDTO> usages(
            @ContextSource SymbolDTO symbol,
            @Name("indexId") @Optional String indexId,
            @Name("limit") @Optional int limit) {
        if (indexId == null)
            return Collections.emptyList();
        return codeIndexService.getSymbolUsages(indexId,
                symbol.getId(), limit > 0 ? limit : 20)
                .stream()
                .map(AnnotationUsageDTO::fromCodeAnnotationUsage)
                .collect(Collectors.toList());
    }

    @BizLoader(forType = SymbolDTO.class)
    public String sourceCode(
            @ContextSource SymbolDTO symbol,
            @Name("indexId") @Optional String indexId,
            @Name("linesBefore") @Optional int linesBefore,
            @Name("linesAfter") @Optional int linesAfter) {
        if (indexId == null)
            return null;
        return codeIndexService.getSymbolSourceCode(indexId,
                symbol.getId(), linesBefore, linesAfter > 0 ? linesAfter : 5);
    }

    @BizQuery
    public SymbolSourceDTO showSymbol(
            @Name("indexId") String indexId,
            @Name("qualifiedName") String qualifiedName,
            @Name("includeBody") @Optional boolean includeBody) {
        return codeIndexService.showSymbolSource(indexId, qualifiedName, includeBody);
    }

    @BizQuery
    public List<ModuleDigestDTO> moduleDigest(
            @Name("indexId") String indexId,
            @Name("dirPath") String dirPath,
            @Name("includePrivate") @Optional boolean includePrivate) {
        return codeIndexService.getModuleDigest(indexId, dirPath, includePrivate);
    }

    @BizQuery
    public List<PublicAPIDTO> publicSurface(
            @Name("indexId") String indexId,
            @Name("dirPath") String dirPath) {
        return codeIndexService.getPublicSurface(indexId, dirPath);
    }

    @BizQuery
    public List<TypeOutlineDTO> batchGetOutlines(
            @Name("qualifiedNames") List<String> qualifiedNames,
            @Name("indexId") String indexId) {
        return codeIndexService.batchGetTypeOutlines(indexId, qualifiedNames);
    }

    @BizQuery
    public TypeHierarchyDTO getTypeHierarchy(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId,
            @Name("direction") String direction,
            @Name("maxDepth") int maxDepth) {
        String dir = direction != null ? direction : "both";
        int depth = maxDepth > 0 ? maxDepth : 5;
        return codeIndexService.getTypeHierarchy(indexId, qualifiedName, dir, depth);
    }

    @BizQuery
    public CallHierarchyDTO getCallHierarchy(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId,
            @Name("direction") String direction,
            @Name("maxDepth") int maxDepth) {
        String dir = direction != null ? direction : "both";
        int depth = maxDepth > 0 ? maxDepth : 3;
        return codeIndexService.getCallHierarchy(indexId, qualifiedName, dir, depth);
    }

    @BizQuery
    public FileOutlineDTO fileOutline(
            @Name("indexId") String indexId,
            @Name("filePath") String filePath) {
        return codeIndexService.getFileOutline(indexId, filePath);
    }

    @BizQuery
    public List<CodeSearchResultDTO> searchCode(
            @Name("indexId") String indexId,
            @Name("query") String query,
            @Name("searchType") @Optional String searchType,
            @Name("language") @Optional String language,
            @Name("filePattern") @Optional String filePattern,
            @Name("limit") @Optional int limit) {
        String type = searchType != null ? searchType : "COMBINED";
        int lim = limit > 0 ? limit : 50;
        return codeIndexService.searchCode(indexId, query, type, language, filePattern, lim);
    }

    @BizQuery
    public List<ReferenceDTO> findReferencedBy(
            @Name("indexId") String indexId,
            @Name("qualifiedName") String qualifiedName,
            @Name("kind") @Optional String kind,
            @Name("limit") @Optional int limit) {
        int lim = limit > 0 ? limit : 50;
        return codeIndexService.findReferencedBy(indexId, qualifiedName, kind, lim);
    }

    @BizQuery
    public DeadCodeReport detectDeadCode(@Name("indexId") String indexId) {
        return codeIndexService.detectDeadCode(indexId);
    }

    @BizQuery
    public List<SymbolDTO> findByAnnotation(
            @Name("indexId") String indexId,
            @Name("annotationName") String annotationName) {
        return codeIndexService.findByAnnotation(indexId, annotationName).stream()
                .map(SymbolDTO::fromCodeSymbol)
                .collect(Collectors.toList());
    }

    @BizQuery
    public List<SymbolDTO> findImplementations(
            @Name("indexId") String indexId,
            @Name("qualifiedName") String qualifiedName,
            @Name("directOnly") @Optional boolean directOnly,
            @Name("maxDepth") @Optional int maxDepth) {
        int depth = maxDepth > 0 ? maxDepth : 10;
        return codeIndexService.findImplementations(indexId, qualifiedName, directOnly, depth).stream()
                .map(SymbolDTO::fromCodeSymbol)
                .collect(Collectors.toList());
    }
}
