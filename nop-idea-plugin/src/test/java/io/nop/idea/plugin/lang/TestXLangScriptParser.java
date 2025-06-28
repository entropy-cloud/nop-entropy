package io.nop.idea.plugin.lang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import io.nop.idea.plugin.lang.script.XLangScriptFileType;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class TestXLangScriptParser extends LightPlatformCodeInsightFixture4TestCase {
    private static final String ext = XLangScriptFileType.INSTANCE.getDefaultExtension();

    @Test
    public void testImportStatement() {
        String code = "import io.nop.core.model.query.QueryBeanHelper;";

        checkMatchJavaParseTree(code);
    }

    protected void checkMatchJavaParseTree(String code) {
        PsiFile testFile = myFixture.configureByText("sample." + ext, code);
        String testTree = toParseTreeText(testFile);

        PsiFile javaFile = myFixture.configureByText("sample.java", code);
        String javaTree = toParseTreeText(javaFile);

        assertEquals(javaTree, testTree);
    }

    protected String toParseTreeText(@NotNull PsiElement file) {
        return DebugUtil.psiToString(file, false, false);
    }
}
