package io.nop.ai.coder;

import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.markdown.simple.MarkdownCodeBlock;
import io.nop.markdown.simple.MarkdownCodeBlockParser;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.ooxml.markdown.DocxToMarkdownConverter;
import io.nop.task.ITaskFlowManager;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xmeta.SchemaLoader;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static io.nop.xlang.XLangErrors.ERR_XDSL_UNKNOWN_PROP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestAiCoderHelper extends JunitBaseTestCase {
    @Inject
    IPromptTemplateManager promptTemplateManager;

    @Inject
    ITaskFlowManager taskFlowManager;

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
        Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources("/nop/ai/prompts", "prompt.yaml");
        for (IResource resource : resources) {
            if (resource.getName().endsWith(".prompt.yaml")) {
                promptTemplateManager.loadPromptTemplateFromPath(resource.getPath());
            }
        }
    }

    @Test
    public void parseAllTasks() {
        Collection<? extends IResource> resources = VirtualFileSystem.instance().getAllResources("/nop/ai/tasks", ".task.xml");
        for (IResource resource : resources) {
            taskFlowManager.loadTaskFromPath(resource.getStdPath());
        }
    }

    @Test
    public void parseAllSchema() {
        List<? extends IResource> resources = VirtualFileSystem.instance().getChildren("/nop/ai/schema/coder");
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

    @Disabled
    @Test
    public void testDocumentExt() {
        IResource resource = new FileResource(new File(getModuleDir(), "demo/requirements/index.md"));
        MarkdownDocument doc = MarkdownTool.instance().parseFromResource(resource);
        doc.matchTplFromPath("/nop/ai/schema/coder/requirements.tpl.md", true);
        MarkdownTool.instance().loadChildSections(doc.getRootSection(), 2);
        //doc.getRootSection().matchTplForSection("/nop/ai/schema/coder/module-requirements.tpl.md", true);

        MarkdownDocument ormDoc = doc.selectSectionByTplTag("ORM", false);
        System.out.println(ormDoc.toText());
    }

    @Test
    public void testParseJavaCode() {
        String text = attachmentText("test-java-code.md");
        MarkdownCodeBlock block = new MarkdownCodeBlockParser().parseCodeBlockForLang(null, text, "java");
        System.out.println(block.getSource());
    }

    @Test
    public void testXDefRef() {
        XNode node = AiXDefHelper.loadXDefForAi("/nop/ai/schema/coder/api.xdef");
        node.dump();
        assertTrue(node.childByTag("orm:delta").hasChild());
    }

    @Test
    public void testDocxToMarkdown() {
        DocxToMarkdownConverter converter = new DocxToMarkdownConverter();
        MarkdownDocument doc = converter.convertFromResource(attachmentResource("requirements.docx"));
        System.out.println(doc.toText());
    }

    @Test
    public void testDocxToMarkdownTitle() {
        DocxToMarkdownConverter converter = new DocxToMarkdownConverter();
        MarkdownDocument doc = converter.convertFromResource(attachmentResource("test-markdown.docx"));
        System.out.println(doc.toText());
        assertEquals(normalizeCRLF(attachmentText("test-markdown.md")), doc.toText());
    }

    @Test
    public void testMatchMarkdownTpl() {
        MarkdownDocument doc = MarkdownTool.instance().parseFromResource(attachmentResource("output-db.md"));
        IResource tplResource = VirtualFileSystem.instance().getResource("/nop/ai/schema/coder/db-detail-design.tpl.md");
        MarkdownDocument tpl = MarkdownTool.instance().parseFromResource(tplResource);
        doc.matchTpl(tpl, true);
    }

    @Test
    public void testObjSchemaJava() {
        String code = AiCoderHelper.getObjMetaJava("/nop/schema/api.xdef");
        System.out.println(code);
    }
}
