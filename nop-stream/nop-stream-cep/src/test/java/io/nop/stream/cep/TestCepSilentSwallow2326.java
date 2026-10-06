/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.cep.model.CepPatternModel;
import io.nop.stream.cep.model.CepPatternSingleModel;
import io.nop.stream.cep.model.FollowKind;
import io.nop.stream.cep.model.builder.CepPatternBuilder;
import io.nop.stream.cep.pattern.MalformedPatternException;
import io.nop.stream.cep.pattern.Pattern;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 FLOW-01 pin (plan 2326): a pattern part declaring {@code windowTime}
 * WITHOUT a quantifier (oneOrMore/times/timesOrMore) is rejected at build time
 * — the within window has nothing to apply to and was previously silently
 * dropped. Reverting the guard in {@code CepPatternBuilder.addQualifier} turns
 * this test red (the build would succeed and silently ignore the window).
 */
class TestCepSilentSwallow2326 {

    private IEvalFunction alwaysTrue() {
        return (thisObj, args, scope) -> true;
    }

    @Test
    void windowTimeWithoutQuantifierIsRejected() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel part = new CepPatternSingleModel();
        part.setName("start");
        part.setWhere(alwaysTrue());
        part.setWindowTime(Duration.ofSeconds(5));
        // NO quantifier (oneOrMore/times/timesOrMore all absent)
        model.addPart(part);

        CepPatternBuilder builder = new CepPatternBuilder();
        io.nop.stream.cep.pattern.MalformedPatternException ex = assertThrows(io.nop.stream.cep.pattern.MalformedPatternException.class,
                () -> builder.buildFromModel(model));
        assertTrue(String.valueOf(ex.getParam("patternDetail")).contains("windowTime"),
                () -> "windowTime-without-quantifier must be typed-rejected: "
                        + ex.getParam("patternDetail"));
    }

    @Test
    void windowTimeWithQuantifierStillBuilds() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel part = new CepPatternSingleModel();
        part.setName("start");
        part.setWhere(alwaysTrue());
        part.setWindowTime(Duration.ofSeconds(5));
        part.setOneOrMore(true);
        model.addPart(part);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        org.junit.jupiter.api.Assertions.assertNotNull(pattern,
                "windowTime WITH a quantifier must still build (legal shape)");
    }
}
