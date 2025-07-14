package io.nop.report.docx;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.ooxml.docx.WordTemplate;
import io.nop.report.docx.parse.XptWordTemplateParser;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

public class TestXptWordTemplate extends JunitBaseTestCase {
    @Test
    public void testXptTable() {
        IResource resource = getResource("/test/test-word-report.docx");
        WordTemplate tpl = new XptWordTemplateParser().parseFromResource(resource);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("data", Arrays.asList(Map.of("name", "a", "amount", 100),
                Map.of("name", "b", "amount", 200)));
        tpl.generateToFile(getTargetFile("test-result.docx"), scope);
    }
}
