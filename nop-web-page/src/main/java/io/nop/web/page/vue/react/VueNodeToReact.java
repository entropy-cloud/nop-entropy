package io.nop.web.page.vue.react;

import io.nop.commons.text.IndentPrinter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.web.page.vue.VueNode;
import io.nop.web.page.vue.VueSlot;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.TemplateExpression;

import java.util.List;

/**
 * 蒋vue模板的解析结果转换为React的createElement调用
 */
public class VueNodeToReact {
    public void render(VueNode node, IndentPrinter out) {
        renderFor(node, out);
    }

    private void renderFor(VueNode node, IndentPrinter out) {
        if (node.getItemsExpr() == null) {
            renderIf(node, out);
        } else {
            String varName = node.getItemVarName();
            if (varName == null)
                varName = "_";

            out.append('(').append(node.getItemsExpr().toExprString());
            out.append(").map((");
            out.append(varName);
            if (node.getIndexVarName() != null) {
                out.append(',').append(node.getIndexVarName());
            }
            out.append(")=>{").incIndent();
            out.indent().append("return ");
            renderIf(node, out);
            out.decIndent().indent().append("}");
        }
    }

    private void renderIf(VueNode node, IndentPrinter out) {
        if (node.getIfExpr() == null) {
            renderNode(node, out);
        } else {
            out.append(node.getIfExpr().toExprString());
            out.append(" ? ");
            renderNode(node, out);
            out.append(": null");
        }
    }

    private void renderNode(VueNode node, IndentPrinter out) {
        out.append("h(").append(getElementName(node)).append(',').incIndent();
        if (!hasReactProp(node)) {
            out.indent().append("null,");
        } else {
            out.indent().append('{').incIndent();
            if (node.getHtmlExpr() != null) {
                out.indent().append("dangerouslySetInnerHTML: { __html:");
                out.append(node.getHtmlExpr().toExprString()).append("},");
            }

            if (node.getProps() != null) {
                node.getProps().forEach((name, value) -> {
                    out.indent().append(getPropName(name)).append(':');
                    if (value instanceof String) {
                        out.append(StringHelper.quote(value.toString()));
                    } else if (value instanceof Expression) {
                        out.append(((Expression) value).toExprString());
                    } else {
                        out.append(String.valueOf(value));
                    }
                    out.append(',');
                });
            }

            if (node.getSlots() != null) {
                node.getSlots().values().forEach(slot -> {
                    out.indent().append(getPropName(slot.getSlotName())).append(':');
                    appendSlot(out, slot);
                });
            }

            if (node.getEventHandlers() != null) {
                node.getEventHandlers().forEach((name, handler) -> {
                    out.indent().append(getEventName(name)).append(':');
                    out.append(handler.toExprString());
                });
            }
            out.decIndent().indent().append("},");
        }

        if (node.getContentExpr() != null) {
            out.indent().append(getTemplateExpr(node.getContentExpr()));
        } else {
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                out.indent().append("null");
            } else {
                out.indent().append('[').incIndent();
                for (VueNode child : node.getChildren()) {
                    out.indent();
                    render(child, out);
                    out.append(',');
                }
                out.decIndent().indent().append(']');
            }
        }
        out.decIndent().indent().append(')');
    }

    private boolean hasReactProp(VueNode node) {
        if (node.getHtmlExpr() != null)
            return true;
        if (node.getProps() != null && !node.getProps().isEmpty())
            return true;
        if (node.getEventHandlers() != null && !node.getEventHandlers().isEmpty())
            return true;
        if (node.getSlots() != null && !node.getSlots().isEmpty())
            return true;
        return false;
    }

    protected String getPropName(String name) {
        if (name.equals("class"))
            return "className";
        if (name.equals("for"))
            return "htmlFor";
        return StringHelper.camelCase(name, '-', false);
    }

    protected String getElementName(VueNode node) {
        if ("template".equals(node.getType()))
            return "Fragment";

        String componentName = node.getComponentName();
        if (componentName != null)
            return componentName;
        return "\"" + node.getType() + "\"";
    }

    protected String getEventName(String name) {
        return "on" + StringHelper.capitalize(name);
    }

    protected void appendSlot(IndentPrinter out, VueSlot slot) {
        String var = slot.getSlotVar();
        if (var == null)
            var = "_";

        out.append(" ").append(var).append(" => {").incIndent();
        out.indent().append("return ");
        if (slot.getContentExpr() != null) {
            out.append(slot.getContentExpr().toExprString());
        } else if (slot.getChildren() != null) {
            if (slot.getChildren().size() == 1) {
                renderNode(slot.getChildren().get(0), out);
            } else {
                out.append('[').incIndent();
                for (VueNode child : slot.getChildren()) {
                    render(child, out);
                    out.append(',');
                }
                out.decIndent().indent().append(']');
            }
        }
        out.decIndent().indent().append("}");
    }

    private String getTemplateExpr(Expression expr) {
        if (expr == null)
            return "null";
        if (expr instanceof TemplateExpression) {
            StringBuilder sb = new StringBuilder();
            List<Expression> exprs = ((TemplateExpression) expr).getExpressions();
            for (Expression subExpr : exprs) {
                if (sb.length() != 0) {
                    sb.append('+');
                }
                sb.append(subExpr.toExprString());
            }
            return sb.toString();
        } else {
            return expr.toExprString();
        }
    }
}