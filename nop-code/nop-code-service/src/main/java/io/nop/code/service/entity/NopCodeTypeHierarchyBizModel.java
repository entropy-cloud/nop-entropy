package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.TypeHierarchyDTO;
import jakarta.inject.Inject;

@BizModel("NopCodeTypeHierarchy")
public class NopCodeTypeHierarchyBizModel {

    @Inject
    protected ICodeIndexService codeIndexService;

    @BizQuery
    public TypeHierarchyDTO get(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId,
            @Name("direction") String direction,
            @Name("maxDepth") int maxDepth) {
        String dir = direction != null ? direction : "both";
        int depth = maxDepth > 0 ? maxDepth : 5;
        return codeIndexService.getTypeHierarchy(indexId, qualifiedName, dir, depth);
    }
}
