package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.TypeOutlineDTO;
import jakarta.inject.Inject;

import java.util.List;

@BizModel("NopCodeType")
public class NopCodeTypeBizModel {

    @Inject
    protected ICodeIndexService codeIndexService;

    @BizQuery
    public CodeSymbol get(@Name("id") String id, @Name("indexId") String indexId) {
        return codeIndexService.getSymbolById(indexId, id);
    }

    @BizQuery
    public CodeSymbol findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        return codeIndexService.findSymbolByQualifiedName(indexId, qualifiedName);
    }

    @BizQuery
    public List<TypeOutlineDTO> batchGetOutlines(
            @Name("qualifiedNames") List<String> qualifiedNames,
            @Name("indexId") String indexId) {
        return codeIndexService.batchGetTypeOutlines(indexId, qualifiedNames);
    }
}
