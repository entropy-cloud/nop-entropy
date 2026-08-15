package io.nop.ai.gateway;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.CandidateHealthProvider;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.routing.IModelClassHealth;
import io.nop.ai.core.routing.ISelectionStrategy;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.routing.RuleBasedSelectionStrategy;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5b IoC wiring test (plan 2026-08-15-1116-1, Phase 3): verifies the
 * {@code nopAiRuleBasedSelectionStrategy} bean wiring under the two deployment
 * forms (mirrors the {@code TestChannelMessageServiceIoC} real-container
 * pattern — {@code AppBeanContainerLoader} + a test beans.xml that reproduces
 * the production bean definition in isolation; AppnLoader does not load
 * autoconfig).
 * <ul>
 *   <li><b>Rule engine present</b>: the test beans file imports the REAL
 *       platform {@code rule-defaults.beans.xml}; the strategy bean resolves
 *       and {@code ruleManager} is injected non-null. Behavioral Anti-Hollow:
 *       a {@code select()} call runs the REAL injected manager against the
 *       nop-ai-core test rule ({@code rule-selection/preferred/v1}) and
 *       returns the rule-chosen candidate.</li>
 *   <li><b>Rule engine absent</b>: without rule-defaults the {@code ioc:optional}
 *       ref resolves to null while the container still starts; the strategy
 *       fails fast on first use ({@code ERR_AI_AGENT_INVALID_ARG}) — no silent
 *       fallback.</li>
 * </ul>
 */
class TestRuleBasedSelectionStrategyIoC {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainer startContainer(String resourcePath) {
        IResource resource = VirtualFileSystem.instance().getResource(resourcePath);
        IBeanContainer container = new AppBeanContainerLoader()
                .loadFromResource("test-rule-strategy-ioc", resource);
        container.start();
        return container;
    }

    private static ModelClassCandidate candidate(String provider, String model, String accountKey) {
        return new ModelClassCandidate(provider, model, accountKey, null, null);
    }


    @Test
    void ruleEnginePresentInjectsManagerAndSelects() {
        IBeanContainer container = startContainer("/test/beans/test-rule-based-selection-strategy-ioc.beans.xml");
        try {
            ISelectionStrategy strategy = (ISelectionStrategy)
                    container.getBean("nopAiRuleBasedSelectionStrategy");
            assertNotNull(strategy, "strategy bean must exist");
            assertTrue(strategy instanceof RuleBasedSelectionStrategy,
                    "bean must be the RuleBasedSelectionStrategy implementation");

            // Behavioral Anti-Hollow: the REAL injected nopRuleManager (from
            // rule-defaults.beans.xml) executes the nop-ai-core test rule —
            // proving the wiring is runtime-live, not a text-only property.
            ThresholdBreaker breaker = new ThresholdBreaker(3, 60_000L);
            IModelClassHealth health = new CandidateHealthProvider(breaker, new ConcurrencyRegistry());
            List<ModelClassCandidate> candidates = List.of(
                    candidate("test-accounts", "deepseek-v4", null),
                    candidate("test-accounts", "deepseek-v4", "key-backup-1"));
            ModelClassCandidate selected = strategy.select(
                    io.nop.ai.api.chat.ChatRequest.userPrompt("hi"), candidates, health, Set.of());
            assertNotNull(selected, "rule manager must be wired and the rule must select");
            assertEquals("key-backup-1", selected.getAccountKey(),
                    "rule decision (index 1) must flow through the injected manager");
        } finally {
            container.stop();
        }
    }

    @Test
    void ruleEngineAbsentStartsWithNullManagerAndFailsFastOnUse() {
        IBeanContainer container = startContainer("/test/beans/test-rule-based-selection-strategy-ioc-norule.beans.xml");
        try {
            RuleBasedSelectionStrategy strategy = (RuleBasedSelectionStrategy)
                    container.getBean("nopAiRuleBasedSelectionStrategy");
            assertNotNull(strategy, "strategy bean must exist even without the rule engine");
            // the ioc:optional ref resolved to null (mirroring production wiring)
            assertNull(readRuleManager(strategy),
                    "without rule-defaults loaded, the optional ruleManager ref must resolve to null");

            // first use fails fast — no silent fallback to the default strategy
            IModelClassHealth health = new CandidateHealthProvider(new ThresholdBreaker(3, 60_000L),
                    new ConcurrencyRegistry());
            NopException e = assertThrows(NopException.class,
                    () -> strategy.select(io.nop.ai.api.chat.ChatRequest.userPrompt("hi"),
                            List.of(candidate("test-accounts", "deepseek-v4", null)), health, Set.of()),
                    "unwired ruleManager must fail fast on first select");
            assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode());
        } finally {
            container.stop();
        }
    }

    /**
     * Read the package-private-in-spirit {@code ruleManager} field (no public
     * getter — injection point, not part of the usage contract), mirroring the
     * {@code readMessageService} pattern in TestChannelMessageServiceIoC.
     */
    private static Object readRuleManager(RuleBasedSelectionStrategy strategy) {
        try {
            Field f = RuleBasedSelectionStrategy.class.getDeclaredField("ruleManager");
            f.setAccessible(true);
            return f.get(strategy);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("unable to read ruleManager field", e);
        }
    }
}
