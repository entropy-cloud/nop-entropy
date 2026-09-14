package io.nop.ai.toolkit.tools;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.VirtualFileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8-P1 round-4 (Phase 2): guards the PRODUCTION beans file
 * {@code /nop/ai/beans/ai-toolkit-defaults.beans.xml} against two regressions:
 *
 * <ol>
 *   <li>someone silently wires a sandbox into the default {@code ai-tools:bash} bean —
 *       the fail-closed default (no backend wired = every call refused) must be preserved;</li>
 *   <li>the opt-in assembly example drifts from the real sandbox class names / property name.
 *   </li>
 * </ol>
 *
 * <p>Runs against the VFS copy of the file (no IoC container needed — the toolkit test
 * classpath does not include an {@code IHttpClient} implementation, so a full app-container
 * bootstrap is intentionally avoided).
 */
public class TestBashToolDefaultWiring {

    private static final String BEANS_PATH = "/nop/ai/beans/ai-toolkit-defaults.beans.xml";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static String beansText() {
        return VirtualFileSystem.instance().getResource(BEANS_PATH).readText();
    }

    @Test
    void defaultBashBeanKeepsFailClosedWithoutSandbox() {
        XNode root = XNode.parse(beansText());
        XNode bashBean = findBean(root, "ai-tools:bash");
        assertNotNull(bashBean, "ai-tools:bash bean must be defined in " + BEANS_PATH);
        assertEquals("io.nop.ai.toolkit.tools.BashExecutor", bashBean.getAttr("class"),
                "the default ai-tools:bash bean must remain a plain BashExecutor");
        assertEquals(0, bashBean.getChildCount(),
                "the default ai-tools:bash bean must not carry a sandbox property/constructor-arg "
                        + "— unwired backend keeps the tool fail-closed");
    }

    @Test
    void optInExampleNamesRealSandboxClasses() {
        String text = beansText();
        assertTrue(text.contains("io.nop.ai.toolkit.tools.sandbox.HostBashSandbox"),
                "the opt-in example must reference the real HostBashSandbox class");
        assertTrue(text.contains("io.nop.ai.toolkit.tools.sandbox.DockerBashSandbox"),
                "the opt-in example must reference the real DockerBashSandbox class");
        assertTrue(text.contains("nopBashHostSandbox") && text.contains("nopBashDockerSandbox"),
                "the opt-in example bean ids must stay stable (nopBashHostSandbox/nopBashDockerSandbox)");
        assertTrue(text.contains("<property name=\"sandbox\" ref=\"nopBashHostSandbox\"/>"),
                "the opt-in example must wire the sandbox via BashExecutor's setSandbox property");
    }

    private static XNode findBean(XNode root, String id) {
        for (XNode child : root.getChildren()) {
            if (child.getTagName().equals("bean") && id.equals(child.getAttr("id"))) {
                return child;
            }
        }
        return null;
    }
}