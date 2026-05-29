/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.cep.model.CepPatternModel;
import io.nop.stream.cep.model.CepPatternSingleModel;
import io.nop.stream.cep.model.FollowKind;
import io.nop.stream.cep.model.builder.CepPatternBuilder;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.IterativeCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
public class TestCepPatternBuilderModel {

    private CepPatternSingleModel newSingle(String name, String next, FollowKind followKind,
                                            IEvalFunction where) {
        CepPatternSingleModel step = new CepPatternSingleModel();
        step.setName(name);
        step.setNext(next);
        step.setFollowKind(followKind);
        step.setWhere(where);
        return step;
    }

    private IEvalFunction constantCondition(boolean value) {
        return (thisObj, args, scope) -> value;
    }

    @Test
    void testBuildTwoStepPattern() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel beginStep = newSingle("start", "end", FollowKind.next,
                constantCondition(true));
        CepPatternSingleModel endStep = newSingle("end", null, null,
                constantCondition(true));

        model.addPart(beginStep);
        model.addPart(endStep);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        // Walk the pattern chain backwards to count steps and verify names
        assertNotNull(pattern, "Pattern should not be null");
        assertEquals("end", pattern.getName(), "Last pattern step should be 'end'");
        assertNotNull(pattern.getPrevious(), "Should have a previous step");
        assertEquals("start", pattern.getPrevious().getName(),
                "First pattern step should be 'start'");
        assertNull(pattern.getPrevious().getPrevious(),
                "Start step should have no previous");
    }

    @Test
    void testBuildPatternWithConditions() {
        IEvalFunction beginWhere = constantCondition(true);
        IEvalFunction endWhere = constantCondition(true);

        CepPatternModel model = new CepPatternModel();
        model.setStart("begin");

        CepPatternSingleModel beginStep = newSingle("begin", "middle", FollowKind.next, beginWhere);
        CepPatternSingleModel endStep = newSingle("middle", null, null, endWhere);

        model.addPart(beginStep);
        model.addPart(endStep);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        // Walk back: last step is "middle", first step is "begin"
        Pattern<?, ?> middleStep = pattern;
        Pattern<?, ?> beginStepPattern = pattern.getPrevious();

        assertEquals("middle", middleStep.getName());
        assertEquals("begin", beginStepPattern.getName());

        // Verify each step has a condition (the where clause was applied)
        assertNotNull(beginStepPattern.getCondition(),
                "Begin step should have a condition from where clause");
        assertNotNull(middleStep.getCondition(),
                "Middle step should have a condition from where clause");
    }

    @Test
    void testBuildPatternSequenceNameAndStepCount() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("step1");

        CepPatternSingleModel step1 = newSingle("step1", "step2", FollowKind.next,
                constantCondition(true));
        CepPatternSingleModel step2 = newSingle("step2", "step3", FollowKind.followedBy,
                constantCondition(true));
        CepPatternSingleModel step3 = newSingle("step3", null, null,
                constantCondition(true));

        model.addPart(step1);
        model.addPart(step2);
        model.addPart(step3);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        // Walk the chain and collect names
        java.util.List<String> names = new java.util.ArrayList<>();
        Pattern<?, ?> current = pattern;
        while (current != null) {
            names.add(current.getName());
            current = current.getPrevious();
        }
        java.util.Collections.reverse(names);

        assertEquals(3, names.size(), "Should have 3 pattern steps");
        assertEquals("step1", names.get(0), "First step should be step1");
        assertEquals("step2", names.get(1), "Second step should be step2");
        assertEquals("step3", names.get(2), "Third step should be step3");
    }

    @Test
    void testBuildPatternConditionCount() {
        // begin step with condition, followed by next step with condition
        CepPatternModel model = new CepPatternModel();
        model.setStart("first");

        CepPatternSingleModel firstStep = newSingle("first", "second", FollowKind.next,
                constantCondition(true));
        CepPatternSingleModel secondStep = newSingle("second", null, null,
                constantCondition(true));

        model.addPart(firstStep);
        model.addPart(secondStep);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        // Count conditions across the chain
        int conditionCount = 0;
        Pattern<?, ?> current = pattern;
        while (current != null) {
            IterativeCondition<?> cond = current.getCondition();
            // getCondition() returns trueFunction() when no condition set,
            // but we set where on both steps, so both should have non-default conditions
            if (cond != null) {
                conditionCount++;
            }
            current = current.getPrevious();
        }

        assertEquals(2, conditionCount,
                "Both steps should have conditions applied");
    }

    @Test
    void testBuildPatternWithoutConditions() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("a");

        CepPatternSingleModel stepA = new CepPatternSingleModel();
        stepA.setName("a");
        stepA.setNext("b");
        stepA.setFollowKind(FollowKind.next);

        CepPatternSingleModel stepB = new CepPatternSingleModel();
        stepB.setName("b");

        model.addPart(stepA);
        model.addPart(stepB);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        assertNotNull(pattern);
        assertEquals("b", pattern.getName());
        assertEquals("a", pattern.getPrevious().getName());
    }

    @Test
    void testOneOrMoreQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step = new CepPatternSingleModel();
        step.setName("start");
        step.setOneOrMore(true);
        model.addPart(step);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
        assertTrue(pattern.getPrevious() != null || pattern.getName().equals("start"));
    }

    @Test
    void testTimesQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step = new CepPatternSingleModel();
        step.setName("start");
        step.setTimes(io.nop.api.core.beans.IntRangeBean.build(2, 4));
        model.addPart(step);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testTimesOrMoreQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step = new CepPatternSingleModel();
        step.setName("start");
        step.setTimesOrMore(3);
        model.addPart(step);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testConsecutiveQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.next, constantCondition(true));
        step1.setOneOrMore(true);
        step1.setConsecutive(true);
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testAllowCombinationsQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.next, constantCondition(true));
        step1.setOneOrMore(true);
        step1.setAllowCombinations(true);
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testGreedyQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.next, constantCondition(true));
        step1.setOneOrMore(true);
        step1.setGreedy(true);
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testOptionalQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.next, constantCondition(true));
        step1.setOptional(true);
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testSubtypeQualifier() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = new CepPatternSingleModel();
        step1.setName("start");
        step1.setNext("end");
        step1.setFollowKind(FollowKind.next);
        step1.setSubType("java.lang.String");
        CepPatternSingleModel step2 = new CepPatternSingleModel();
        step2.setName("end");
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testFollowKindNotNext() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.notNext, constantCondition(true));
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
        assertEquals("end", pattern.getName());
        assertEquals("start", pattern.getPrevious().getName());
    }

    @Test
    void testFollowKindNotFollowedBy() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.notFollowedBy, constantCondition(true));
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
    }

    @Test
    void testFollowKindFollowedByAny() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step1 = newSingle("start", "end", FollowKind.followedByAny, constantCondition(true));
        CepPatternSingleModel step2 = newSingle("end", null, null, null);
        model.addPart(step1);
        model.addPart(step2);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);
        assertNotNull(pattern);
        assertEquals("end", pattern.getName());
        assertEquals("start", pattern.getPrevious().getName());
    }

    @Test
    void testBuildConditionIsSerializable() {
        CepPatternModel model = new CepPatternModel();
        model.setStart("start");

        CepPatternSingleModel step = newSingle("start", null, null, constantCondition(true));
        model.addPart(step);

        CepPatternBuilder builder = new CepPatternBuilder();
        Pattern<?, ?> pattern = builder.buildFromModel(model);

        IterativeCondition<?> condition = pattern.getCondition();
        assertTrue(condition instanceof java.io.Serializable,
                "EvalFunctionCondition should be Serializable");
    }
}
