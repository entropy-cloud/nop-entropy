package io.nop.ai.coder;

import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.simple.MarkdownDocumentExt;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xmeta.SchemaLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static io.nop.xlang.XLangErrors.ERR_XDSL_UNKNOWN_PROP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled
public class TestAiCoderHelper extends JunitBaseTestCase {
    @Inject
    IPromptTemplateManager promptTemplateManager;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void parseAllPrompts() {
        List<? extends IResource> resources = VirtualFileSystem.instance().getChildren("/nop/ai/prompts/coder");
        for (IResource resource : resources) {
            if (resource.getName().endsWith(".prompt.yaml")) {
                promptTemplateManager.loadPromptTemplateFromPath(resource.getPath());
            }
        }
    }

    @Test
    public void parseAllSchema() {
        List<? extends IResource> resources = VirtualFileSystem.instance().getChildren("/nop/ai/prompts/coder");
        for (IResource resource : resources) {
            if (resource.getName().endsWith(".xdef")) {
                SchemaLoader.loadXDefinition(resource.getStdPath());
            } else if (resource.getName().endsWith(".tpl.md")) {
                MarkdownTool.loadMarkdownTpl(resource.getStdPath());
            }
        }
    }

    @Test
    public void testInvalidPrompt() {
        try {
            promptTemplateManager.loadPromptTemplateFromPath("/test/invalid.prompt.yaml");
            fail();
        } catch (NopException e) {
            assertEquals(ERR_XDSL_UNKNOWN_PROP.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testExpr() {
        String expr = "import io.nop.ai.coder.orm.AiOrmModel;\n" +
                "      return AiOrmModel.buildFromAiResult(value,{basePackageName: 'app.demo'});";

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("value", XNode.make("orm"));

        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        IEvalAction action = cp.compileFullExpr(null, expr);
        action.invoke(scope);
    }

    @Test
    public void testDocumentExt() {
        IResource resource = new FileResource(new File(getModuleDir(), "demo/refactored-requirements.md"));
        MarkdownDocument doc = MarkdownTool.instance().parseFromResource(resource);
        doc.matchTplFromPath("/nop/ai/schema/coder/requirements.tpl.md", true);
        MarkdownDocumentExt ext = MarkdownTool.instance().loadDocumentExt(doc);
        ext.matchTplForSection("/nop/ai/schema/coder/module-requirements.tpl.md", true);
        ext.mergeToDocument(doc);

        MarkdownDocument ormDoc = doc.selectSectionByTplTag("ORM", false);
        System.out.println(ormDoc.toText());
    }
}
