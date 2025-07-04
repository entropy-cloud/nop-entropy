package io.nop.ai.core.prompt.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;

import java.util.ArrayList;
import java.util.List;

public class PromptExprParser {

    public static class TextExpr implements IPromptExpr {
        private final String text;
        private final SourceLocation loc;

        TextExpr(SourceLocation loc, String text) {
            this.loc = loc;
            this.text = text;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void renderTo(StringBuilder sb, IEvalScope scope) {
            sb.append(text);
        }
    }

    public static class VariableExpr implements IPromptExpr {
        private final String varName;
        private final SourceLocation loc;

        VariableExpr(SourceLocation loc, String varName) {
            this.loc = loc;
            this.varName = varName;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void renderTo(StringBuilder sb, IEvalScope scope) {
            Object value = scope.getValue(varName);
            if (value != null)
                sb.append(value.toString());
        }
    }

    public static class PrefixExpr implements IPromptExpr {
        private final String prefix;
        private final String arg;
        private final SourceLocation loc;

        PrefixExpr(SourceLocation loc, String prefix, String arg) {
            this.loc = loc;
            this.prefix = prefix;
            this.arg = arg;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void renderTo(StringBuilder sb, IEvalScope scope) {
            sb.append(prefix).append(':').append(arg);
        }
    }

    public static class CompositeExpr implements IPromptExpr {
        private final List<IPromptExpr> exprs;
        private final SourceLocation loc;

        CompositeExpr(SourceLocation loc, List<IPromptExpr> exprs) {
            this.loc = loc;
            this.exprs = exprs;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void renderTo(StringBuilder sb, IEvalScope scope) {
            for (IPromptExpr expr : exprs) {
                expr.renderTo(sb, scope);
            }
        }
    }

    public IPromptExpr parse(SourceLocation loc, String input) {
        if (loc == null)
            loc = SourceLocation.fromPath("text");
        
        List<IPromptExpr> exprs = new ArrayList<>();
        int pos = 0;
        int len = input.length();

        while (pos < len) {
            int start = input.indexOf("{{", pos);
            if (start < 0) {
                exprs.add(new TextExpr(loc, input.substring(pos)));
                break;
            }

            if (start > pos) {
                exprs.add(new TextExpr(loc, input.substring(pos, start)));
            }

            int end = input.indexOf("}}", start + 2);
            if (end < 0) {
                exprs.add(new TextExpr(loc, input.substring(start)));
                break;
            }

            String content = input.substring(start + 2, end).trim();
            int colonPos = content.indexOf(':');
            if (colonPos > 0) {
                String prefix = content.substring(0, colonPos).trim();
                String arg = content.substring(colonPos + 1).trim();
                exprs.add(buildPrefixExpr(loc, prefix, arg));
            } else {
                exprs.add(buildVariableExpr(loc, content));
            }

            pos = end + 2;
        }

        return new CompositeExpr(loc, exprs);
    }

    protected PrefixExpr buildPrefixExpr(SourceLocation loc, String prefix, String arg) {
        return new PrefixExpr(loc, prefix, arg);
    }

    protected VariableExpr buildVariableExpr(SourceLocation loc, String varName) {
        return new VariableExpr(loc, varName);
    }
}