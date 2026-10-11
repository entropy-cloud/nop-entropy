package io.nop.ai.deepwiki;

import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deepwiki AI resource smoke (plan 2415): the deepwiki task XML still passes
 * the ai.xlib {@code TaskStep-generator} transform after the tool-invocation
 * retirement, and the deepwiki prompt template still loads and resolves its
 * declared inputs/outputs through {@code IPromptTemplateManager}. No LLM call.
 */
@NopTestConfig(testBeansFile = "/nop/ai/beans/deepwiki-test-mock.beans.xml")
public class TestDeepwikiResourcesLoad extends JunitBaseTestCase {

    @Test
    public void testGenerateReadmeTaskTransformsThroughGenerator() {
        XNode taskNode = XNode.parse(VirtualFileSystem.instance()
                .getResource("/nop/ai/tasks/deepwiki/generate-readme.task.xml").readText());
        XNode step = null;
        for (XNode child : taskNode.childByTag("steps").getChildren()) {
            if ("ai:TaskStep".equals(child.attrText("customType"))) {
                step = child;
                break;
            }
        }
        assertNotNull(step, "deepwiki task must declare an ai:TaskStep step");

        IXplTag generator = XplLibHelper.getTag("/nop/ai/xlib/ai.xlib", "TaskStep-generator", true);
        assertNotNull(generator);

        Map<String, Object> args = new HashMap<>();
        args.put("node", step);
        args.put("_dsl_root", taskNode);
        XNode transformed = generator.generateNode(XLang.newEvalScope(new HashMap<String, Object>()), args);

        assertEquals("step", transformed.getTagName());
        XNode source = transformed.childByTag("source");
        XNode execNode = source == null ? null : source.childByTag("ai:Execute");
        assertNotNull(execNode, "ai:TaskStep must transform into an ai:Execute step");
    }

    @Test
    public void testGenerateReadmePromptLoadsAndValidatesInputs() {
        IPromptTemplateManager manager = BeanContainer.getBeanByType(IPromptTemplateManager.class);
        IPromptTemplate template = manager.loadPromptTemplateFromPath("/nop/ai/prompts/deepwiki/generate-readme.prompt.yaml");

        assertNotNull(template.getInput("fileTree"), "fileTree input must be declared");
        assertNotNull(template.getInput("gitRepository"), "gitRepository input must be declared");

        Map<String, Object> vars = new HashMap<>();
        vars.put("fileTree", "src/");
        vars.put("gitRepository", "/test/repo");
        IEvalScope scope = template.prepareInputs(vars);
        String prompt = template.generatePrompt(scope);
        assertTrue(prompt.contains("src/"), "rendered prompt must embed the fileTree input");
    }
}
