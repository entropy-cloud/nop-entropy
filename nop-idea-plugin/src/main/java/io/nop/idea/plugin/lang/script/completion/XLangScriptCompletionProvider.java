package io.nop.idea.plugin.lang.script.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.ProcessingContext;
import io.nop.idea.plugin.lang.script.psi.ImportSourceNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 * @deprecated 通过实现 {@link PsiElement#getReferences()} 并返回 Java 相关的引用对象，便可实现自动补全，无需单独编写逻辑
 */
@Deprecated
public class XLangScriptCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(
            @NotNull CompletionParameters parameters, @NotNull ProcessingContext context,
            @NotNull CompletionResultSet result
    ) {
        int startOffset = parameters.getOffset();
        PsiElement position = parameters.getPosition();
        PsiElement astContext = getAstContext(position);

        if (astContext instanceof ImportSourceNode imp) {
            String pkgName = imp.getText().substring(0, startOffset - imp.getTextOffset());
            addImportCompletions(imp, pkgName, result);
        }
    }

    protected PsiElement getAstContext(@NotNull PsiElement element) {
        PsiElement parent = element;

        while (parent != null) {
            if (parent instanceof ImportSourceNode) {
                return parent;
            }
            parent = parent.getParent();
        }
        return element.getContainingFile();
    }

    private void addImportCompletions(ImportSourceNode context, String pkgName, CompletionResultSet result) {
        Project project = context.getProject();

        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        AllClassesSearch.search(scope, project).forEach((cls) -> {
            String fqn = cls.getQualifiedName();
            if (fqn == null || fqn.equals(pkgName) || (!pkgName.isEmpty() && !fqn.startsWith(pkgName))) {
                return;
            }

            result.addElement(LookupElementBuilder.create(fqn)
                                                  .withIcon(AllIcons.Nodes.Class)
                                                  .withInsertHandler((ctx, item) -> {
                                                      addImportStatement(ctx, pkgName);
                                                  }));
        });
    }

    private void addImportStatement(InsertionContext context, String pkgName) {
        // 补全插入的后处理
        // 对 Document 的修改可能会导致 AST 重建，从而使得对字符的定位不准，需调整补全插入思路
        Editor editor = context.getEditor();
        Document document = editor.getDocument();

        // 补全插入的开始位置
        int startOffset = context.getStartOffset();
        // 补全插入的结束位置（默认光标位置）
        int tailOffset = context.getTailOffset();
        // 触发补全的字符（Enter, Tab 等）
        char completionChar = context.getCompletionChar();

        int endOffset = startOffset + pkgName.length() - 2;
        document.deleteString(startOffset, endOffset);

//        // 添加括号
//        document.insertString(tailOffset, "()");
//        // 移动光标到括号内
//        editor.getCaretModel().moveToOffset(tailOffset + 1);

//        // 插入带占位符的参数列表
//        document.insertString(tailOffset, "(<arg1>, <arg2>)");
//        // 选择第一个参数占位符
//        int start = tailOffset + 1;
//        int end = start + "<arg1>".length();
//        editor.getSelectionModel().setSelection(start, end);
    }
}
