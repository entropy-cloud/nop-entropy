package io.nop.stream.cep.operator;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFA;
import io.nop.stream.cep.nfa.State;
import io.nop.stream.cep.nfa.StateTransition;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.RichIterativeCondition;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.functions.RuntimeContext;
import io.nop.stream.core.configuration.Configuration;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2511 C1-001 / C10-003 rewrite: {@code copyForSubtask()} must give every
 * subtask PRIVATE deep copies of the NFA factory (hence the pattern State/condition
 * graph) and the user function. The old assertions pinned the defective sharing
 * ({@code assertSame(userFunction)} — sharing IS the defect); they are inverted
 * here and complemented with a parallel-open runtimeContext probe covering the
 * three defect faces (shared condition graph / ctx slot last-writer-wins /
 * open×N + cross close).
 */
public class TestCepOperatorSubtaskCopy {

    /** Static named function: no enclosing-instance capture, Serializable for the round trip. */
    public static class NameMatchFunction extends PatternProcessFunction<Event, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
            Event start = match.get("start").get(0);
            Event end = match.get("end").get(0);
            out.collect(start.getName() + "->" + end.getName());
        }
    }

    /**
     * Rich condition: NFA.open()/close() mutate its runtimeContext in place —
     * shared instances across subtasks would make the context last-writer-wins.
     */
    public static class ProbeRichCondition extends RichIterativeCondition<Event> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean filter(Event value, Context<Event> ctx) {
            return true;
        }
    }

    private static RuntimeContext ctx(int subtask) {
        return new RuntimeContext() {
            @Override
            public int getIndexOfThisSubtask() {
                return subtask;
            }

            @Override
            public int getNumberOfParallelSubtasks() {
                return 2;
            }

            @Override
            public String getTaskName() {
                return "cep-probe-" + subtask;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static NFACompiler.NFAFactory<Event> nfaFactoryOf(CepOperator<Event, Integer, String> op)
            throws Exception {
        Field f = CepOperator.class.getDeclaredField("nfaFactory");
        f.setAccessible(true);
        return (NFACompiler.NFAFactory<Event>) f.get(op);
    }

    private static RichIterativeCondition<Event> firstRichCondition(NFA<Event> nfa) {
        for (State<Event> state : nfa.getStates()) {
            for (StateTransition<Event> t : state.getStateTransitions()) {
                if (t.getCondition() instanceof RichIterativeCondition) {
                    return (RichIterativeCondition<Event>) t.getCondition();
                }
            }
        }
        return null;
    }

    @Test
    void cepOperatorCopyForSubtaskReturnsDistinctInstance() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, new NameMatchFunction(), null);

        CepOperator<Event, Integer, String> copy = operator.copyForSubtask();

        assertNotSame(operator, copy, "CepOperator copy must be a fresh instance");
        // C10-003: the old assertion pinned the defect (assertSame = shared user
        // function across subtasks). Sharing is the defect body — inverted.
        assertNotSame(operator.getUserFunction(), copy.getUserFunction(),
                "CepOperator user function must be a private per-subtask deep copy");

        // Face 1: the compiled State graph must not be shared — each subtask's
        // factory builds an NFA over its own State instances.
        NFA<Event> nfaOriginal = nfaFactoryOf(operator).createNFA();
        NFA<Event> nfaCopy = nfaFactoryOf(copy).createNFA();
        State<Event> originalState = nfaOriginal.getStates().iterator().next();
        State<Event> copyState = nfaCopy.getStates().iterator().next();
        assertNotSame(originalState, copyState, "NFA State graph must be private per subtask");
    }

    /**
     * Faces 2+3 probe (C1-005 ctx slot / C1-006 open×N, cross close): two
     * subtask copies open their NFAs with DIFFERENT runtime contexts; each
     * subtask's Rich condition must hold ITS OWN context (pre-fix both saw the
     * last writer's context).
     */
    @Test
    void cepOperatorSubtaskConditionsHoldOwnRuntimeContextAfterParallelOpen() throws Exception {
        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(new ProbeRichCondition())
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);

        CepOperator<Event, Integer, String> template = new CepOperator<>(
                null, false, nfaFactory, null, null, new NameMatchFunction(), null);
        CepOperator<Event, Integer, String> subtask0 = template.copyForSubtask();
        CepOperator<Event, Integer, String> subtask1 = template.copyForSubtask();

        Configuration emptyConf = new Configuration() {
        };
        NFA<Event> nfa0 = nfaFactoryOf(subtask0).createNFA();
        NFA<Event> nfa1 = nfaFactoryOf(subtask1).createNFA();
        nfa0.open(ctx(0), emptyConf);
        nfa1.open(ctx(1), emptyConf);

        RichIterativeCondition<Event> cond0 = firstRichCondition(nfa0);
        RichIterativeCondition<Event> cond1 = firstRichCondition(nfa1);
        assertTrue(cond0 != null, "probe rich condition must be present in the compiled graph");
        assertNotSame(cond0, cond1, "condition instances must be private per subtask");
        assertEquals(0, cond0.getRuntimeContext().getIndexOfThisSubtask(),
                "subtask 0's condition must hold subtask 0's runtime context");
        assertEquals(1, cond1.getRuntimeContext().getIndexOfThisSubtask(),
                "subtask 1's condition must hold subtask 1's runtime context");
    }

    /**
     * plan 2511 deviation (shareable-leaf carve-out, live red evidence
     * {@code TestS1CdcPipelineE2E} / {@code _tmp/2511-s1-solo.log}): XPL
     * scripted pattern conditions wrap compiled XLang eval functions that are
     * NOT Serializable. The condition OBJECT must still be deep-copied
     * (condition-graph face stays closed), while the stateless compiled
     * function leaf is shared by reference — it implements no RichFunction
     * lifecycle, so none of the three faces reopens. A non-xlang
     * non-Serializable leaf must still fail typed (no silent fallback).
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cepOperatorCopySharesOnlyCompiledXlangFunctionLeaves() throws Exception {
        io.nop.xlang.exec.TestOnlyScriptedEvalFunction leaf =
                new io.nop.xlang.exec.TestOnlyScriptedEvalFunction();

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where((io.nop.stream.cep.pattern.conditions.IterativeCondition)
                        new io.nop.stream.cep.model.builder.EvalFunctionCondition(leaf))
                .followedBy("end")
                .where((io.nop.stream.cep.pattern.conditions.IterativeCondition)
                        new io.nop.stream.cep.model.builder.EvalFunctionCondition(leaf));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);
        CepOperator<Event, Integer, String> template = new CepOperator<>(
                null, false, nfaFactory, null, null, new NameMatchFunction(), null);

        CepOperator<Event, Integer, String> copy = template.copyForSubtask();

        // Condition objects remain private per subtask (face 1 intact).
        NFA<Event> nfaOriginal = nfaFactoryOf(template).createNFA();
        NFA<Event> nfaCopy = nfaFactoryOf(copy).createNFA();
        assertNotSame(nfaOriginal.getStates().iterator().next(),
                nfaCopy.getStates().iterator().next(),
                "NFA State graph must stay private per subtask");

        // The compiled XLang leaf is shared by reference (whitelisted carve-out).
        io.nop.stream.cep.pattern.conditions.IterativeCondition<Event> cond0 =
                firstCondition(nfaOriginal);
        io.nop.stream.cep.pattern.conditions.IterativeCondition<Event> cond1 =
                firstCondition(nfaCopy);
        assertNotSame(cond0, cond1, "condition instances must stay private per subtask");
        java.lang.reflect.Field leafField =
                io.nop.stream.cep.model.builder.EvalFunctionCondition.class.getDeclaredField("action");
        leafField.setAccessible(true);
        Object leaf0 = leafField.get(cond0);
        Object leaf1 = leafField.get(cond1);
        assertSame(leaf0, leaf1,
                "stateless compiled XLang function leaves must be shared by reference");
        assertSame(leaf, leaf0, "shared leaf must be the ORIGINAL compiled function instance");
    }

    private static io.nop.stream.cep.pattern.conditions.IterativeCondition<Event> firstCondition(
            NFA<Event> nfa) {
        for (State<Event> state : nfa.getStates()) {
            for (StateTransition<Event> t : state.getStateTransitions()) {
                if (t.getCondition() != null) {
                    return (io.nop.stream.cep.pattern.conditions.IterativeCondition<Event>) t.getCondition();
                }
            }
        }
        return null;
    }

    /**
     * A non-xlang, non-Serializable leaf is NOT whitelisted: the round trip
     * must keep failing typed (no silent sharing fallback).
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cepOperatorCopyFailsTypedForNonSerializableNonXlangLeaf() {
        class LocalStatefulFn implements io.nop.core.lang.eval.IEvalFunction {
            int calls;

            @Override
            public Object invoke(Object thisObj, Object[] args, io.nop.core.lang.eval.IEvalScope scope) {
                calls++;
                return Boolean.TRUE;
            }
        }

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where((io.nop.stream.cep.pattern.conditions.IterativeCondition)
                        new io.nop.stream.cep.model.builder.EvalFunctionCondition(new LocalStatefulFn()));

        NFACompiler.NFAFactory<Event> nfaFactory = NFACompiler.compileFactory(pattern, false);
        CepOperator<Event, Integer, String> template = new CepOperator<>(
                null, false, nfaFactory, null, null, new NameMatchFunction(), null);

        org.junit.jupiter.api.Assertions.assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                template::copyForSubtask,
                "non-Serializable non-xlang condition leaf must fail typed");
    }
}
