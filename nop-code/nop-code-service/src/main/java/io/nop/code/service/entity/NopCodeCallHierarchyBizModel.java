package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.CallHierarchyDTO;
import jakarta.inject.Inject;

@BizModel("NopCodeCallHierarchy")
public class NopCodeCallHierarchyBizModel {

    @Inject
    protected ICodeIndexService codeIndexService;

    @BizQuery
    public CallHierarchyDTO get(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId,
            @Name("direction") String direction,
            @Name("maxDepth") int maxDepth) {
        String dir = direction != null ? direction : "both";
        int depth = maxDepth > 0 ? maxDepth : 3;
        return codeIndexService.getCallHierarchy(indexId, qualifiedName, dir, depth);
    }
}
