/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match;

import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.match.compile.PatternMatchPatternCompiler;
import io.nop.match.pattern.AlwaysFalseMatchPattern;
import io.nop.match.pattern.BetweenMatchPattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestMatchPattern extends BaseTestCase {
    @Test
    public void testMatch() {
        MatchPatternCompileConfig config = new MatchPatternCompileConfig();
        Object tpl = attachmentBean("match_tpl.json", Object.class);
        Object data = attachmentBean("data.json", Object.class);
        IMatchPattern pattern = PatternMatchPatternCompiler.INSTANCE.parseFromValue(null, tpl, config);

        System.out.println(JsonTool.serialize(pattern.toJson(), true));

        pattern = PatternMatchPatternCompiler.INSTANCE.parseFromValue(null, pattern.toJson(), config);

        MatchState state = new MatchState(data);
        IEvalScope scope = EvalExprProvider.newEvalScope();
        scope.setLocalValue(null, "x", 1);
        state.setScope(scope);

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        state.setErrorCollector(collector);
        pattern.matchValue(state, true);
        System.out.println(JsonTool.serialize(collector.getErrors(), true));
        assertEquals(attachmentJsonText("errors.json"), JsonTool.serialize(collector.getErrors(), true));
    }

    @Test
    public void testBetweenCollectError() {
        // between 校验失败时错误详情必须进入 collector（与同族 Eq/CompareOp 等一致）
        BetweenMatchPattern pattern = new BetweenMatchPattern("between",
                (value, min, max, excludeMin, excludeMax) -> false, 10, 20, false, false);

        MatchState state = new MatchState(25);
        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        state.setErrorCollector(collector);

        assertFalse(pattern.matchValue(state, true));
        assertEquals(1, collector.getErrors().size());
        assertEquals(MatchErrors.ERR_MATCH_BETWEEN_CHECK_FAIL.getErrorCode(),
                collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testAlwaysFalseCollectError() {
        AlwaysFalseMatchPattern pattern = AlwaysFalseMatchPattern.INSTANCE;

        MatchState state = new MatchState(1);
        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        state.setErrorCollector(collector);

        assertFalse(pattern.matchValue(state, true));
        assertEquals(1, collector.getErrors().size());
        assertEquals(MatchErrors.ERR_MATCH_ASSERT_OP_MATCH_FAIL.getErrorCode(),
                collector.getErrors().get(0).getErrorCode());
    }
}