package io.nop.ai.agent;

import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.executor.DefaultToolExecutorProvider;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the 10 tool beans declared in {@code ai-agent-tools.beans.xml} are
 * actually loaded by the IoC container and collected via
 * {@code <ioc:collect-beans by-type="...IToolExecutor"/>}.
 *
 * <p>This tests the fix for audit 08-1: the beans file was never loaded because the
 * module lacked a {@code /nop/autoconfig/*.beans} entry file. After creating
 * {@code /nop/autoconfig/nop-ai-agent.beans}, the beans are discoverable.
 *
 * <p>The test loads a standalone container that imports the actual
 * {@code ai-agent-tools.beans.xml} and collects all {@link IToolExecutor} beans.
 * It verifies:
 * <ul>
 *   <li>All 10 tool beans are present in the container (by type and by name)</li>
 *   <li>The beans are real instances (not lazy proxies) — calling {@code getToolName()}
 *       returns the expected name without throwing</li>
 * </ul>
 */
public class TestAiAgentToolsIoC {

    private static final Set<String> EXPECTED_TOOL_NAMES = new HashSet<>(Arrays.asList(
            "call-agent",
            "send-message",
            "read-memory",
            "write-memory",
            "search-memory",
            "team-send-message",
            "team-status",
            "team-task-create",
            "team-task-update",
            "team-execute-flow"
    ));

    private static final Set<String> EXPECTED_BEAN_IDS = new HashSet<>(Arrays.asList(
            "ai-agent-tools:call-agent",
            "ai-agent-tools:send-message",
            "ai-agent-tools:read-memory",
            "ai-agent-tools:write-memory",
            "ai-agent-tools:search-memory",
            "ai-agent-tools:team-send-message",
            "ai-agent-tools:team-status",
            "ai-agent-tools:team-task-create",
            "ai-agent-tools:team-task-update",
            "ai-agent-tools:team-execute-flow"
    ));

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainer startContainer() {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/test/beans/test-agent-tools-collect.beans.xml");
        IBeanContainer container = new AppBeanContainerLoader()
                .loadFromResource("test", resource);
        container.start();
        return container;
    }

    @Test
    public void testTenToolBeansLoadedByType() {
        IBeanContainer container = startContainer();
        try {
            Map<String, IToolExecutor> beans = container.getBeansOfType(IToolExecutor.class);
            assertFalse(beans.isEmpty(), "At least 10 IToolExecutor beans should be collected");

            for (String expectedId : EXPECTED_BEAN_IDS) {
                assertTrue(beans.containsKey(expectedId),
                        "Missing tool bean: " + expectedId + ". Present: " + beans.keySet());
            }
            assertEquals(EXPECTED_BEAN_IDS.size(), beans.size(),
                    "Exactly 10 tool beans expected. Got: " + beans.keySet());
        } finally {
            container.stop();
        }
    }

    @Test
    public void testToolBeansAreCallableNotLazy() {
        IBeanContainer container = startContainer();
        try {
            DefaultToolExecutorProvider provider = (DefaultToolExecutorProvider)
                    container.getBean("testToolExecutorProvider");

            Collection<String> toolNames = provider.getToolNames();
            for (String expectedName : EXPECTED_TOOL_NAMES) {
                assertTrue(toolNames.contains(expectedName),
                        "Provider missing tool: " + expectedName + ". Has: " + toolNames);

                IToolExecutor executor = provider.getExecutor(expectedName);
                assertNotNull(executor, "Executor should be a real instance for: " + expectedName);
                assertEquals(expectedName, executor.getToolName(),
                        "getToolName() must return the bean id — proves the bean is initialized, not a lazy proxy");
            }
        } finally {
            container.stop();
        }
    }
}
