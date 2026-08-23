package io.nop.sys.service.entity;

import io.nop.core.context.ServiceContextImpl;
import io.nop.sys.dao.coderule.SysCodeRuleGenerator;
import io.nop.sys.dao.entity.NopSysCodeRule;
import io.nop.sys.dao.entity.NopSysSequence;
import io.nop.sys.dao.seq.SysSequenceGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * check 审计 nop-sys 两条缓存失效条目的回归：
 * <ul>
 *   <li>[P2] 序列配置缓存无失效——管理端修改 nextValue/cacheSize 原不生效且会被内存轨迹写回覆盖；</li>
 *   <li>[P3] 编码规则每次生成查询规则表——缓存后经 afterEntityChange 失效。</li>
 * </ul>
 */
public class TestSysConfigCacheInvalidation {

    static class RecordingSeqGenerator extends SysSequenceGenerator {
        String removedKey;

        @Override
        public void removeCache(String cacheKey) {
            removedKey = cacheKey;
        }
    }

    static class RecordingCodeRuleGenerator extends SysCodeRuleGenerator {
        int clears;

        @Override
        public void clearCache() {
            clears++;
        }
    }

    static class WrappingSeqBizModel extends NopSysSequenceBizModel {
        void fireAfterEntityChange(NopSysSequence entity) {
            afterEntityChange(entity, "update", new ServiceContextImpl());
        }
    }

    static class WrappingCodeRuleBizModel extends NopSysCodeRuleBizModel {
        void fireAfterEntityChange(NopSysCodeRule entity) {
            afterEntityChange(entity, "save", new ServiceContextImpl());
        }
    }

    @Test
    public void testSequenceConfigChangeInvalidatesGeneratorCache() {
        WrappingSeqBizModel bizModel = new WrappingSeqBizModel();
        RecordingSeqGenerator generator = new RecordingSeqGenerator();
        bizModel.sequenceGenerator = generator;

        NopSysSequence seq = new NopSysSequence();
        seq.setSeqName("my-seq");
        bizModel.fireAfterEntityChange(seq);

        assertEquals("my-seq", generator.removedKey, "sequence config change must evict the generator's SeqItem");
    }

    @Test
    public void testCodeRuleConfigChangeClearsRuleCache() {
        WrappingCodeRuleBizModel bizModel = new WrappingCodeRuleBizModel();
        RecordingCodeRuleGenerator generator = new RecordingCodeRuleGenerator();
        bizModel.codeRuleGenerator = generator;

        bizModel.fireAfterEntityChange(new NopSysCodeRule());
        assertEquals(1, generator.clears);

        bizModel.fireAfterEntityChange(new NopSysCodeRule());
        assertEquals(2, generator.clears, "each change must clear again (no latching)");
    }
}
