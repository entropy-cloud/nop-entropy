package io.nop.ai.coder;

import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model.PromptModel;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAiCoder extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    PromptModel loadPrompt(String promptPath) {
        PromptModel promptModel = (PromptModel) ResourceComponentManager.instance().loadComponentModel(promptPath);
        return promptModel;
    }

    @Test
    public void testRefineRequirements() {
        PromptModel promptModel = loadPrompt("/nop/ai/prompts/coder/refine-requirements.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        vars.put("requirements", attachmentText("requirements.md"));

        IEvalScope scope = promptModel.prepareInputs(vars);

        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);

        AiChatResponse response = new AiChatResponse();
        String content = attachmentText("response-refine-requirements.md");
        response.setContent(content);
        promptModel.processChatResponse(response, scope);

        System.out.println(response.getContent());
    }

    @Test
    public void testOrmDesign() {
        PromptModel promptModel = loadPrompt("/nop/ai/prompts/coder/orm-design.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        vars.put("requirements", attachmentText("response-refine-requirements.md"));

        IEvalScope scope = promptModel.prepareInputs(vars);
        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);
        assertEquals(normalizeCRLF(attachmentText("prompt-orm-design.md")), normalizeCRLF(prompt));

        AiChatResponse response = new AiChatResponse();
        String content = attachmentText("response-orm-design.md");
        response.setContent(content);
        promptModel.processChatResponse(response, scope);

        XNode node = (XNode) response.getOutput("RESULT");
        node.dump();
    }

    @Test
    public void testRefineOrmDesign() {
        PromptModel promptModel = loadPrompt("/nop/ai/prompts/coder/refine-orm-design.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        vars.put("ormModelText", attachmentText("output-orm-design.xml"));
        vars.put("requirements", attachmentText("response-refine-requirements.md"));

        IEvalScope scope = promptModel.prepareInputs(vars);
        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);
        assertEquals(normalizeCRLF(attachmentText("prompt-refine-orm-design.md").trim()), normalizeCRLF(prompt.trim()));

        AiChatResponse response = new AiChatResponse();
        String content = attachmentText("response-refine-orm-design.md");
        response.setContent(content);
        promptModel.processChatResponse(response, scope);

        assertTrue(response.isValid());
        assertNull(response.getOutput("RESULT"));
        assertEquals(true, response.getOutput("noChange"));
    }
}
