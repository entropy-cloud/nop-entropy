package io.nop.ai.core.prompt;

import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.ai.core.prompt.node.PromptSyntaxParser;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true)
public class TestPromptSyntaxParser extends JunitBaseTestCase {
    @Test
    public void testPrompt() {
        IResource resource = getResource("/test/test.prompt.md");
        IPromptSyntaxNode expr = new PromptSyntaxParser().enableInclude(true).parseFromResource(resource);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("varA", "a");
        scope.setLocalValue("varB", "b");
        String text = expr.render(scope);
        assertEquals("测试变量ab\n" +
                "测试引入[SUB]", text.trim());
    }
}
