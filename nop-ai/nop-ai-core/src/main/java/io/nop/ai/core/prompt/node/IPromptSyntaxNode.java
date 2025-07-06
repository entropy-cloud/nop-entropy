package io.nop.ai.core.prompt.node;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.source.IWithSourceCode;

import java.util.LinkedHashSet;
import java.util.Set;

import static io.nop.ai.core.AiCoreErrors.ARG_VAR_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_NO_VAR_IN_SCOPE;

public interface IPromptSyntaxNode extends ISourceLocationGetter, IWithSourceCode {

    String toString();

    default String getSource() {
        StringBuilder sb = new StringBuilder();
        accept(new IPromptSyntaxNodeVisitor() {
            @Override
            public void visitText(PromptSyntaxParser.TextNode expr) {
                sb.append(expr.getText());
            }

            @Override
            public void visitVariable(PromptSyntaxParser.VariableNode expr) {
                sb.append("{{").append(expr.getVarName()).append("}}");
            }

            @Override
            public void visitPrefix(PromptSyntaxParser.PrefixNode expr) {
                sb.append("{{").append(expr.getPrefix()).append(':').append(expr.getArg()).append("}}");
            }

            @Override
            public void visitComposite(PromptSyntaxParser.CompositeNode expr) {
                IPromptSyntaxNodeVisitor.super.visitComposite(expr);
            }
        });
        return sb.toString();
    }

    void accept(IPromptSyntaxNodeVisitor visitor);

    default String render(IEvalScope scope) {
        StringBuilder sb = new StringBuilder();
        accept(new IPromptSyntaxNodeVisitor() {
            @Override
            public void visitText(PromptSyntaxParser.TextNode expr) {
                sb.append(expr.getText());
            }

            @Override
            public void visitVariable(PromptSyntaxParser.VariableNode expr) {
                Object value = scope.getValue(expr.getVarName());
                if (value != null) {
                    sb.append(value);
                } else if (!scope.containsValue(expr.getVarName())) {
                    throw new NopException(ERR_AI_NO_VAR_IN_SCOPE)
                            .source(expr)
                            .param(ARG_VAR_NAME, expr.getVarName());
                }
            }

            @Override
            public void visitPrefix(PromptSyntaxParser.PrefixNode expr) {
                sb.append("{{").append(expr.getPrefix()).append(':').append(expr.getArg()).append("}}");
            }
        });
        return sb.toString();
    }

    default Set<String> collectVarNames() {
        Set<String> varNames = new LinkedHashSet<>();
        this.accept(new IPromptSyntaxNodeVisitor() {
            @Override
            public void visitVariable(PromptSyntaxParser.VariableNode expr) {
                varNames.add(expr.getVarName());
            }
        });
        return varNames;
    }

    interface IPromptSyntaxNodeVisitor {
        default void visitText(PromptSyntaxParser.TextNode expr) {
        }

        default void visitVariable(PromptSyntaxParser.VariableNode expr) {
        }

        default void visitPrefix(PromptSyntaxParser.PrefixNode expr) {
        }

        default void visitComposite(PromptSyntaxParser.CompositeNode expr) {
            for (IPromptSyntaxNode subExpr : expr.getExprs()) {
                subExpr.accept(this);
            }
        }
    }
}
