/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI21 E1 (Flink StateMachineExample degradation shape, 12-scenario matrix): per-key
 * DFA in a process bean + checkpoint declaration + dual sinks (main + alert branch).
 *
 * <p>DFA contract (two-state machine, per-entity journal): IDLE --start--> RUNNING;
 * RUNNING --error--> ALERT (alert branch fires once per transition); ALERT --reset-->
 * IDLE. Events: {entity, event} with entity=e1/e2.
 *
 * <p>Rate limiting is degraded to in-bean self-limiting per the WI0 adjudication.
 * The checkpoint-restore full chain is covered by the WI10/WI9 engine suites
 * (TestRecalculateBackfillE2E / TestE2ETwoPhaseCommitSink); E1 pins the declaration
 * + the dual-sink data plane.
 */
public class TestXdslScenarioE1 {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        container = new BeanContainerBuilder(null)
                .addResource(new ClassPathResource(
                        "classpath:_vfs/nop/stream/test/xdsl-e1-statemachine.beans.xml"))
                .build("xdsl-e1-statemachine");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null)
            container.stop();
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    @Test
    public void dfaTransitionsAlertBranchAndDualSinks() throws Exception {
        E1Stores.MAIN.clear();
        E1Stores.ALERT.clear();
        E1Stores.JOURNAL.clear();

        StreamModel model = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/test/xdsl-e1-statemachine.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("xdsl-e1-statemachine");

        // main branch: every non-ALERT transition lands in the main sink in order
        // ALERT-state outputs route to the ALERT sink (the filter split), so the
        // main sink carries RUNNING/IDLE transitions only
        assertEquals(List.of(
                        Map.of("entity", "e1", "state", "RUNNING"),
                        Map.of("entity", "e2", "state", "RUNNING"),
                        Map.of("entity", "e1", "state", "IDLE")),
                E1Stores.MAIN, () -> "main: " + E1Stores.MAIN);
        // alert branch: exactly the two error transitions land in the alert sink
        assertEquals(List.of(
                        Map.of("entity", "e2", "state", "ALERT"),
                        Map.of("entity", "e1", "state", "ALERT")),
                E1Stores.ALERT, () -> "alert: " + E1Stores.ALERT);
        // DFA journal: the full transition sequence (5 events, 5 transitions)
        assertEquals(5, E1Stores.JOURNAL.size(), () -> "journal: " + E1Stores.JOURNAL);
    }
}
