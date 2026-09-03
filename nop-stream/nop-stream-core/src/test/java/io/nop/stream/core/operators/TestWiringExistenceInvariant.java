/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runtime-service injection completeness gate (Cycle 3 / I1, invariant #7): every service
 * injection API on {@link AbstractStreamOperator} consumed by main code must be registered in
 * {@code ai-dev/audits/nop-stream-invariants/wiring-registry.json} (service fqcn x injection API
 * x production wiring points x consumer enumeration x disposition), and production wiring must be
 * reachable at runtime through the task construction path.
 *
 * <p>This is a core-level gate: it must NOT reference nop-stream-runtime / nop-stream-cep
 * classes (dependency direction constraint). The no-service fail-fast / WARN behaviors of
 * {@code CepOperator} / {@code WindowOperator} are asserted by the existing cep / runtime E2E
 * suites ({@code TestCepProductionExecutionE2E} PT-mode no-service fail-fast case +
 * {@code TestProcessingTimeWindowProductionE2E} WARN assertion) — ownership adjudicated, no core
 * duplicate is built.
 *
 * <p>Assertions:
 * <ul>
 *   <li><b>API-surface completeness</b> (registry-driven parameterized): every registry
 *       injection API exists on {@link AbstractStreamOperator} with the registered parameter
 *       type; dispositions are self-consistent (production-wired has wiring points,
 *       internal-creation has none + an on-record reason). Reflective enumeration (V4 forward
 *       direction): any {@code set*} method on {@link AbstractStreamOperator} whose parameter
 *       type is a registered service type and that is NOT in the registry = red (a new
 *       injection API must be registered — {@code setKeyContextElement1/2} (StreamRecord),
 *       {@code setCurrentKey} (Object) etc. are not service injection APIs and never trigger);</li>
 *   <li><b>wiring connectivity</b> (Rule #23): after {@code new StreamTaskInvokable(chain, ...)}
 *       the operators' {@code getProcessingTimeService()} / {@code getTimeServiceManager()} are
 *       non-null, identical instances to the invokable's, and a timer-using operator's
 *       {@code registerTimerService} registration is observable on the manager at runtime
 *       ({@code numTimerServices()});</li>
 *   <li><b>no silent skip</b> (Rule #24): a malformed/empty registry or an unknown disposition
 *       fails loudly; every registered service must be present in the registry tables.</li>
 * </ul>
 *
 * <p>The registry is the single source of truth (mirroring the {@code TestOutputContractInvariant}
 * precedent): adding a service injection API without registering it makes this test red; removing
 * an API requires removing the registry entry (V5 side of the mjs scanner) — both directions are
 * pinned.
 */
public class TestWiringExistenceInvariant {

    static final String REGISTRY_REL_PATH = "ai-dev/audits/nop-stream-invariants/wiring-registry.json";

    /** Raw service parameter type simple names (from the registry services' injectionApi.paramType). */
    static final Set<String> SERVICE_PARAM_SIMPLE_NAMES = new HashSet<>(Arrays.asList(
            "Output", "ProcessingTimeService", "TimerServiceManager",
            "IStateBackend", "IKeyedStateBackend", "IOperatorStateBackend", "Consumer"));

    /**
     * Locate the wiring registry by walking up from the test working directory (surefire runs
     * with basedir = module dir). Fails loudly when not found — no silent skip.
     */
    public static Path findRegistryFile() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            Path candidate = dir.resolve(REGISTRY_REL_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException(
                "wiring-registry.json not found (searched up from " + Paths.get("").toAbsolutePath()
                        + "); no silent skip — run from the repo root");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadRegistry() {
        try {
            Object parsed = JsonTool.parse(Files.readString(findRegistryFile()));
            if (!(parsed instanceof Map)) {
                throw new IllegalStateException("wiring-registry.json is not a JSON object");
            }
            return (Map<String, Object>) parsed;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read wiring-registry.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> registryServices() {
        Map<String, Object> registry = loadRegistry();
        List<Map<String, Object>> services = (List<Map<String, Object>>) registry.get("services");
        if (services == null || services.isEmpty()) {
            throw new IllegalStateException("wiring-registry.json has no services table; "
                    + "no silent skip — every runtime service injection API must be registered here");
        }
        return services;
    }

    /**
     * Registry-driven parameterized source: one row per registered service injection API.
     */
    static Stream<Arguments> registryInjectionApis() {
        List<Arguments> args = new ArrayList<>();
        for (Map<String, Object> service : registryServices()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> api = (Map<String, Object>) service.get("injectionApi");
            if (api == null) {
                fail("service " + service.get("service") + " has no injectionApi (no silent skip)");
            }
            args.add(Arguments.of(
                    service.get("service"),
                    api.get("class"),
                    api.get("method"),
                    api.get("paramType"),
                    api.get("genericArg"),
                    service.get("disposition")));
        }
        return args.stream();
    }

    /**
     * Registry-driven API-surface check: every registered injection API must exist on
     * {@link AbstractStreamOperator} with the registered parameter type, and the disposition
     * must be self-consistent (production-wired has at least one wiring point with an injection
     * timing; internal-creation has none and an on-record reason). A registry entry whose method
     * no longer exists is red (V5 mirror at JUnit level).
     */
    @ParameterizedTest
    @MethodSource("registryInjectionApis")
    void testRegisteredInjectionApiExistsOnAbstractStreamOperator(
            String serviceFqcn, String apiClass, String method, String paramType, String genericArg,
            String disposition) throws Exception {
        assertEquals("io.nop.stream.core.operators.AbstractStreamOperator", apiClass,
                "injection API host must be AbstractStreamOperator for " + method);
        Class<?> paramClazz = Class.forName(String.valueOf(paramType));
        Method declared;
        try {
            declared = AbstractStreamOperator.class.getDeclaredMethod(method, paramClazz);
        } catch (NoSuchMethodException e) {
            // generic-typed injection API (e.g. setSnapshotCallback(Consumer<OperatorSnapshotResult>)):
            // the raw parameter class alone disambiguates single-parameter setters
            declared = null;
            for (Method candidate : AbstractStreamOperator.class.getDeclaredMethods()) {
                if (candidate.getName().equals(method) && candidate.getParameterCount() == 1
                        && candidate.getParameterTypes()[0].equals(paramClazz)) {
                    declared = candidate;
                    break;
                }
            }
            if (declared == null) {
                throw e;
            }
        }
        assertNotNull(declared, "registered injection API " + method + " must exist on AbstractStreamOperator");
        assertEquals(1, declared.getParameterCount(),
                "registered injection API " + method + " must be a single-parameter setter");
        String rawSimple = declared.getParameterTypes()[0].getSimpleName();
        assertEquals(simpleNameOf(paramType), rawSimple,
                "registered injection API " + method + " parameter type mismatch");
        if (genericArg != null) {
            Type genericParam = declared.getGenericParameterTypes()[0];
            assertTrue(genericParam instanceof ParameterizedType,
                    method + " parameter must be generic (" + genericArg + ")");
            String actual = ((ParameterizedType) genericParam).getActualTypeArguments()[0].getTypeName();
            assertTrue(actual.endsWith(simpleNameOf(String.valueOf(genericArg))),
                    method + " generic argument mismatch: expected " + genericArg + " but got " + actual);
        }
        assertTrue(disposition.equals("production-wired") || disposition.equals("internal-creation"),
                "unknown registry disposition '" + disposition + "' for " + method
                        + "; vocabulary = {production-wired, internal-creation}");
        if ("production-wired".equals(disposition)) {
            assertWiringPointsPresent(method, serviceFqcn);
        } else {
            assertInternalCreation(method, serviceFqcn);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertWiringPointsPresent(String method, String serviceFqcn) {
        for (Map<String, Object> service : registryServices()) {
            if (serviceFqcn.equals(service.get("service"))) {
                List<Map<String, Object>> points = (List<Map<String, Object>>) service.get("wiringPoints");
                assertTrue(points != null && !points.isEmpty(),
                        "production-wired service " + method + " must declare at least one wiring point "
                                + "with an injection timing (no silent skip)");
                for (Map<String, Object> point : points) {
                    assertNotNull(point.get("file"), method + " wiring point missing file");
                    assertNotNull(point.get("line"), method + " wiring point missing line");
                    assertNotNull(point.get("injectionTiming"), method + " wiring point missing injectionTiming");
                }
                return;
            }
        }
        fail("service " + serviceFqcn + " not found in registry (no silent skip)");
    }

    @SuppressWarnings("unchecked")
    private static void assertInternalCreation(String method, String serviceFqcn) {
        for (Map<String, Object> service : registryServices()) {
            if (serviceFqcn.equals(service.get("service"))) {
                List<Map<String, Object>> points = (List<Map<String, Object>>) service.get("wiringPoints");
                assertTrue(points == null || points.isEmpty(),
                        "internal-creation service " + method + " must declare NO wiring points (carve-out)");
                assertNotNull(service.get("reason"),
                        "internal-creation service " + method + " must carry an on-record carve-out reason "
                                + "(ratchet-constrained, no silent exemption)");
                return;
            }
        }
        fail("service " + serviceFqcn + " not found in registry (no silent skip)");
    }

    private static String simpleNameOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    /**
     * V4 forward direction (reflective): every {@code set*} method declared on
     * {@link AbstractStreamOperator} whose single parameter type is a registered service type
     * must be in the registry. A new service injection API that is not registered = red.
     * Non-service injection APIs ({@code setKeyContextElement1/2} with StreamRecord,
     * {@code setCurrentKey} with Object) never trigger.
     */
    @Test
    void testApiSurfaceCompletenessEveryServiceSetterIsRegistered() {
        List<String> notRegistered = new ArrayList<>();
        for (Method method : AbstractStreamOperator.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("set") || method.getParameterCount() != 1) {
                continue;
            }
            if (!SERVICE_PARAM_SIMPLE_NAMES.contains(method.getParameterTypes()[0].getSimpleName())) {
                continue;
            }
            if (!isRegistered(method.getName(), method.getParameterTypes()[0])) {
                notRegistered.add(method.getName() + "(" + method.getParameterTypes()[0].getSimpleName() + ")");
            }
        }
        assertTrue(notRegistered.isEmpty(),
                "service injection API(s) not in wiring-registry.json: " + notRegistered
                        + " — a new service injection API must be registered (V4 semantics)");
    }

    private static boolean isRegistered(String methodName, Class<?> paramType) {
        for (Map<String, Object> service : registryServices()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> api = (Map<String, Object>) service.get("injectionApi");
            if (methodName.equals(api.get("method"))
                    && simpleNameOf(String.valueOf(api.get("paramType"))).equals(paramType.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Consumer table sanity: the consumer enumeration of each registered service (the classes
     * that call the service's getter/registration in main code) must be present in the registry's
     * consumerTable, and each consumerTable row must carry a service reference. A consumer whose
     * service is unregistered would be invisible to V2 — fail loudly instead.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testConsumerTableIsConsistentWithServices() {
        Map<String, Object> registry = loadRegistry();
        List<Map<String, Object>> consumerTable = (List<Map<String, Object>>) registry.get("consumerTable");
        assertTrue(consumerTable != null && !consumerTable.isEmpty(),
                "wiring-registry.json consumerTable must enumerate every main consumer class");
        Set<String> registeredServices = new HashSet<>();
        for (Map<String, Object> service : registryServices()) {
            registeredServices.add(String.valueOf(service.get("service")));
            List<String> consumers = (List<String>) service.get("consumers");
            if (consumers != null) {
                for (String consumerFqcn : consumers) {
                    boolean found = consumerTable.stream()
                            .anyMatch(row -> consumerFqcn.equals(row.get("class")));
                    assertTrue(found,
                            "service " + service.get("service") + " lists consumer " + consumerFqcn
                                    + " but the class is absent from consumerTable (no silent skip)");
                }
            }
        }
        for (Map<String, Object> row : consumerTable) {
            String service = String.valueOf(row.get("service"));
            assertTrue(registeredServices.contains(service),
                    "consumerTable row " + row.get("class") + " references unregistered service " + service);
            assertNotNull(row.get("behaviorWithoutService"),
                    "consumerTable row " + row.get("class") + " missing behaviorWithoutService");
        }
        assertFalse(consumerTable.isEmpty(), "consumerTable must not be empty");
    }

    /**
     * Wiring connectivity (Rule #23): the invokable constructor injects the production
     * ProcessingTimeService + TimerServiceManager into every operator BEFORE open(); the
     * operator's getters observe the SAME instances the invokable holds, and a timer-using
     * operator's registerTimerService registration is observable at runtime.
     *
     * <p>Self-contained in this gate class (gate self-sufficiency principle) — mirrors the
     * assertions of {@code TestStreamTaskInvokableProcessingTimeWiring} without depending on it.
     */
    @Test
    void testTaskConstructionWiresServicesIntoOperators() throws Exception {
        TimerRegisteringOperator timerOp = new TimerRegisteringOperator();
        timerOp.setOutput(new io.nop.stream.core.test.TestOutput<>());
        OperatorChain chain = new OperatorChain(List.of(timerOp));

        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        // Wiring evidence at CONSTRUCTION (before any open()).
        assertNotNull(invokable.getProcessingTimeService(), "PTS created at construction");
        assertNotNull(invokable.getTimeServiceManager(), "TimerServiceManager created at construction");
        assertNotNull(timerOp.getProcessingTimeService(), "PTS injected into every operator");
        assertNotNull(timerOp.getTimeServiceManager(), "TimerServiceManager injected into every operator");
        assertSame(invokable.getProcessingTimeService(), timerOp.getProcessingTimeService(),
                "Operator and invokable share the same PTS instance");
        assertSame(invokable.getTimeServiceManager(), timerOp.getTimeServiceManager(),
                "Operator and invokable share the same TimerServiceManager instance");

        invokable.invoke();

        // Rule #23 runtime registration evidence: open() registered the timer service.
        assertEquals(1, invokable.getTimeServiceManager().numTimerServices(),
                "Timer-using operator registered its HeapInternalTimerService during open()");
    }

    /**
     * Operator that mirrors the timer wiring contract of ProcessOperator/WindowOperator: creates
     * a HeapInternalTimerService in open() and registers it with the task's TimerServiceManager.
     */
    static class TimerRegisteringOperator extends AbstractStreamOperator<String>
            implements OneInputStreamOperator<String, String>, Triggerable<String, String> {

        private static final long serialVersionUID = 1L;

        @Override
        public void open() throws Exception {
            super.open();
            HeapInternalTimerService<String, String> internalTimerService =
                    new HeapInternalTimerService<>(this);
            if (getTimeServiceManager() != null) {
                getTimeServiceManager().registerTimerService(internalTimerService);
            }
        }

        @Override
        public void processElement(StreamRecord<String> element) throws Exception {
            output.collect(element);
        }

        @Override
        public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
        }

        @Override
        public void onEventTime(InternalTimer<String, String> timer) throws Exception {
        }
    }
}
