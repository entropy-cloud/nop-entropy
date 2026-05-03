
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeIndexBiz;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.IndexStatsDTO;
import jakarta.inject.Inject;

import java.nio.file.Path;

@BizModel("NopCodeIndex")
public class NopCodeIndexBizModel extends CrudBizModel<NopCodeIndex> implements INopCodeIndexBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeIndexBizModel() {
        setEntityName(NopCodeIndex.class.getName());
    }

    @BizMutation
    public int indexDirectory(
            @Name("indexId") String indexId,
            @Name("directoryPath") String directoryPath,
            @Name("filePattern") String filePattern) {
        Path path = Path.of(directoryPath);
        String pattern = filePattern != null ? filePattern : "**/*.java";
        return codeIndexService.indexDirectory(indexId, path, pattern);
    }

    @BizMutation
    public CodeFileAnalysisResult indexFile(
            @Name("indexId") String indexId,
            @Name("filePath") String filePath,
            @Name("sourceCode") String sourceCode) {
        return codeIndexService.indexFile(indexId, filePath, sourceCode);
    }

    @BizQuery
    public IndexStatsDTO getStats(@Name("indexId") String indexId) {
        return codeIndexService.getIndexStats(indexId);
    }

    @BizMutation
    public boolean deleteIndex(@Name("indexId") String indexId) {
        codeIndexService.deleteIndex(indexId);
        return true;
    }
}
