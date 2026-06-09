package io.nop.ai.agent;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.ai.core.prompt.node.PromptSyntaxParser;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAgentModelLoading {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLoadAgentModel() {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-agent.agent.xml");

        assertNotNull(model, "AgentModel should not be null after loading");
        assertEquals("test-agent", model.getName(), "Agent name should match");
        assertEquals("A minimal test agent", model.getDescription(), "Description should match");

        IPromptSyntaxNode prompt = model.getPrompt();
        assertNotNull(prompt, "Prompt should not be null");
        assertTrue(prompt instanceof PromptSyntaxParser.CompositeNode,
                "Prompt should be a CompositeNode");
        PromptSyntaxParser.CompositeNode composite = (PromptSyntaxParser.CompositeNode) prompt;
        assertEquals(1, composite.getExprs().size(), "Prompt should have one child node");
        assertTrue(composite.getExprs().get(0) instanceof PromptSyntaxParser.TextNode,
                "Prompt child should be a TextNode");
        PromptSyntaxParser.TextNode textNode = (PromptSyntaxParser.TextNode) composite.getExprs().get(0);
        assertEquals("You are a helpful assistant.", textNode.getText(),
                "Prompt text should match");
    }
}
