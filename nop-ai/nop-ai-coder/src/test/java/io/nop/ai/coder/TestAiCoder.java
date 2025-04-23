package io.nop.ai.coder;

import io.nop.ai.coder.orm.AiOrmConfig;
import io.nop.ai.coder.orm.AiOrmModelNormalizer;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model.PromptModel;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
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
        assertEquals(normalizeCRLF(attachmentText("prompt-orm-design.md").trim()), normalizeCRLF(prompt.trim()));

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

    @Test
    public void testMenuDesign() {
        PromptModel promptModel = loadPrompt("/nop/ai/prompts/coder/menu-design.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        vars.put("ormModelText", attachmentText("output-orm-design.xml"));
        vars.put("requirements", attachmentText("response-refine-requirements.md"));

        IEvalScope scope = promptModel.prepareInputs(vars);
        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);
        assertEquals(normalizeCRLF(attachmentText("prompt-menu-design.md").trim()), normalizeCRLF(prompt.trim()));

        AiChatResponse response = new AiChatResponse();
        String content = attachmentText("response-menu-design.md");
        response.setContent(content);
        promptModel.processChatResponse(response, scope);

        assertTrue(response.isValid());
        XNode node = (XNode) response.getOutput("RESULT");
        node.dump();
    }

    @Test
    public void testExtractEntityRequirements() {
        PromptModel promptModel = loadPrompt("/nop/ai/prompts/coder/extract-entity-requirements.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        vars.put("requirements", attachmentText("response-refine-requirements.md"));
        vars.put("entityName", "stock_operation(库存操作)");

        IEvalScope scope = promptModel.prepareInputs(vars);
        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);
        assertEquals(normalizeCRLF(attachmentText("prompt-extract-entity-requirements.md").trim()), normalizeCRLF(prompt.trim()));
    }

    @Test
    public void testFormDesign() {
        PromptModel promptModel = loadPrompt("/nop/ai/prompts/coder/form-design.prompt.yaml");
        Map<String, Object> vars = new HashMap<>();
        vars.put("requirements", attachmentText("response-extract-entity-requirements.md"));

        XNode node = attachmentXml("output-orm-design.xml");
        AiOrmConfig config = new AiOrmConfig();
        config.setBasePackageName("app");
        new AiOrmModelNormalizer().normalizeOrm(node, config);

        OrmModel ormModel = (OrmModel) new DslModelParser().parseFromNode(node);
        IEntityModel entityModel = ormModel.requireEntityModel("StockOperation");
        vars.put("entityModel", entityModel);

        XNode menuNode = attachmentXml("output-menu-design.xml");
        menuNode.setAttr("x:schema", "/nop/ai/schema/auth.xdef");
        Object site = new DslModelParser().parseFromNode(menuNode);
        List<Object> roleList = (List<Object>) BeanTool.getProperty(site, "roles");
        vars.put("roleList", roleList);

        IEvalScope scope = promptModel.prepareInputs(vars);
        String prompt = promptModel.generatePrompt(scope);
        System.out.println(prompt);
        assertEquals(normalizeCRLF(attachmentText("prompt-form-design.md").trim()), normalizeCRLF(prompt.trim()));
    }
}
