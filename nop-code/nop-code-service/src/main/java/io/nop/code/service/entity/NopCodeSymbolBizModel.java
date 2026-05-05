
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.PageBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeSymbolBiz;
import io.nop.code.core.model.CodeAnnotationUsage;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.AnnotationUsageDTO;
import io.nop.code.service.api.dto.CallHierarchyDTO;
import io.nop.code.service.api.dto.SymbolDTO;
import io.nop.code.service.api.dto.TypeHierarchyDTO;
import io.nop.code.service.api.dto.TypeOutlineDTO;
import jakarta.inject.Inject;

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

    @BizLoader
    public List<AnnotationUsageDTO> usages(
            @ContextSource SymbolDTO symbol,
            @Name("indexId") String indexId,
            @Name("limit") int limit) {
        return codeIndexService.getSymbolUsages(indexId != null ? indexId : "test",
                symbol.getId(), limit > 0 ? limit : 20)
                .stream()
                .map(AnnotationUsageDTO::fromCodeAnnotationUsage)
                .collect(Collectors.toList());
    }

    @BizLoader
    public String sourceCode(
            @ContextSource SymbolDTO symbol,
            @Name("indexId") String indexId,
            @Name("linesBefore") int linesBefore,
            @Name("linesAfter") int linesAfter) {
        return codeIndexService.getSymbolSourceCode(indexId != null ? indexId : "test",
                symbol.getId(), linesBefore, linesAfter > 0 ? linesAfter : 5);
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
}
