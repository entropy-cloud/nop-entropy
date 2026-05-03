
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeSymbolBiz;
import io.nop.code.core.model.*;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
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
    public CodeSymbol getById(@Name("id") String id, @Name("indexId") String indexId) {
        return codeIndexService.getSymbolById(indexId, id);
    }

    @BizQuery
    public CodeSymbol findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        return codeIndexService.findSymbolByQualifiedName(indexId, qualifiedName);
    }

    @BizQuery
    public List<CodeSymbol> findSymbols(
            @Name("query") String query,
            @Name("kinds") List<String> kinds,
            @Name("packageName") String packageName,
            @Name("indexId") String indexId,
            @Name("limit") int limit) {
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
        return codeIndexService.findSymbols(indexId, query, kindList, packageName, limit > 0 ? limit : 20);
    }

    @BizLoader
    public List<CodeAnnotationUsage> usages(
            @ContextSource CodeSymbol symbol,
            @Name("indexId") String indexId,
            @Name("limit") int limit) {
        return codeIndexService.getSymbolUsages(indexId != null ? indexId : "test",
                symbol.getId(), limit > 0 ? limit : 20);
    }

    @BizLoader
    public String sourceCode(
            @ContextSource CodeSymbol symbol,
            @Name("indexId") String indexId,
            @Name("linesBefore") int linesBefore,
            @Name("linesAfter") int linesAfter) {
        return codeIndexService.getSymbolSourceCode(indexId != null ? indexId : "test",
                symbol.getId(), linesBefore, linesAfter > 0 ? linesAfter : 5);
    }
}
