package io.nop.metadata.service.mock;

import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.search.NopMetaSearchProcessor;

/**
 * check2 P2-06（2026-08-23 审计）测试 mock：模拟搜索引擎故障。
 *
 * <p>覆盖 {@link NopMetaSearchProcessor#removeFromIndex} 的 fail-closed 默认语义
 * （searchIndexFailOpen=false 时引擎异常被包装为 {@code ERR_SEARCH_INDEX_REMOVE_FAILED} 抛出），
 * 用于验证 BizModel delete 的主实体索引清理失败不回滚 DB 删除
 * （"DB 未删、索引已删"分裂防护，best-effort + WARN 形态）。
 */
public class ThrowingSearchProcessor extends NopMetaSearchProcessor {

    public int removeAttempts = 0;

    public void reset() {
        removeAttempts = 0;
    }

    @Override
    public void removeFromIndex(String entityType, String entityId) {
        removeAttempts++;
        throw new NopMetadataException(NopMetadataErrors.ERR_SEARCH_INDEX_REMOVE_FAILED)
                .param(NopMetadataErrors.ARG_ENTITY_TYPE, entityType)
                .param(NopMetadataErrors.ARG_ENTITY_ID, entityId);
    }
}
