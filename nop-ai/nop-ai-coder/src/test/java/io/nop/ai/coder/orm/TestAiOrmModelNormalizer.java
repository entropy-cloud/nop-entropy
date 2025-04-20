package io.nop.ai.coder.orm;

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

public class TestAiOrmModelNormalizer extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testNormalizer() {
        PromptModel promptModel = (PromptModel) ResourceComponentManager.instance().loadComponentModel("/nop/ai/prompts/coder/orm-design.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        String requirements = attachmentText("requirements.md");
        vars.put("requirements", requirements);
        IEvalScope scope = promptModel.prepareInputs(vars);
        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);

        AiChatResponse response = new AiChatResponse();
        String content = attachmentText("ai-gen-orm.md");
        response.setContent(content);
        promptModel.processChatResponse(response, scope);

        XNode node = (XNode) response.getOutput("RESULT");
        node.dump();
    }
}
