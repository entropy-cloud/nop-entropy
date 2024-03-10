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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}