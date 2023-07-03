/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.schema.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.IndentPrinter;
import io.nop.commons.util.StringHelper;
import io.nop.graphql.core.ast.GraphQLASTNode;
import io.nop.graphql.core.ast.GraphQLASTVisitor;
import io.nop.graphql.core.ast.GraphQLArgument;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLArrayValue;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDirective;
import io.nop.graphql.core.ast.GraphQLDirectiveDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLEnumValueDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLFragment;
import io.nop.graphql.core.ast.GraphQLFragmentSelection;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLInputFieldDefinition;
import io.nop.graphql.core.ast.GraphQLListType;
import io.nop.graphql.core.ast.GraphQLLiteral;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLNonNullType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLObjectValue;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLPropertyValue;
import io.nop.graphql.core.ast.GraphQLScalarDefinition;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.ast.GraphQLUnionTypeDefinition;
import io.nop.graphql.core.ast.GraphQLValue;
import io.nop.graphql.core.ast.GraphQLVariable;
import io.nop.graphql.core.ast.GraphQLVariableDefinition;

import java.util.List;

public class GraphQLSourcePrinter extends GraphQLASTVisitor {

    private final IndentPrinter out = new IndentPrinter(100);

    public String toString() {
        return out.toString();
    }

    public IndentPrinter getOut() {
        return out;
    }

    public String print(GraphQLASTNode node) {
        try {
            visit(node);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return out.toString();
    }

    @Override
    public void visitGraphQLDocument(GraphQLDocument node) {
        for (GraphQLDefinition def : node.getDefinitions()) {
            visit(def);
            out.br();
        }
    }

    @Override
    public void visitGraphQLDirective(GraphQLDirective node) {
        out.append(" @").append(node.getName());
        appendArgs(node.getArguments());
    }

    @Override
    public void visitGraphQLArgument(GraphQLArgument node) {
        out.append(node.getName()).append(':');
        visit(node.getValue());
    }

    @Override
    public void visitGraphQLLiteral(GraphQLLiteral node) {
        Object value = node.getValue();
        if (value instanceof String) {
            out.append(StringHelper.quote(value.toString()));
        } else {
            out.append(String.valueOf(value));
        }
    }

    @Override
    public void visitGraphQLArrayValue(GraphQLArrayValue node) {
        out.append('[');
        out.incIndent();
        for (int i = 0, n = node.size(); i < n; i++) {
            GraphQLValue value = node.getItems().get(i);
            visit(value);
            if (i != n - 1) {
                out.append(", ");
            }
        }
        out.decIndent();
        out.append(']');
    }

    @Override
    public void visitGraphQLObjectValue(GraphQLObjectValue node) {
        out.append('{').append(" ");
        out.incIndent();
        for (int i = 0, n = node.getProperties().size(); i < n; i++) {
            visit(node.getProperties().get(i));
            if (i != n - 1)
                out.append(", ");
        }
        out.decIndent();
        out.append('}');
    }

    @Override
    public void visitGraphQLPropertyValue(GraphQLPropertyValue node) {
        out.append(node.getName()).append(':');
        visit(node.getValue());
    }

    @Override
    public void visitGraphQLVariable(GraphQLVariable node) {
        out.append("$" + node.getName());
    }

    @Override
    public void visitGraphQLOperation(GraphQLOperation node) {
        printDesc(node.getDescription());
        out.append(node.getOperationType().name());
        out.append(' ');
        if (node.getName() != null) {
            out.append(node.getName());
        }
        if (node.getVariableDefinitions() != null) {
            for (int i = 0, n = node.getVariableDefinitions().size(); i < n; i++) {
                GraphQLVariableDefinition var = node.getVariableDefinitions().get(i);
                visit(var);
                if (i != n - 1)
                    out.append(", ");
            }
        }

        visitChildren(node.getDirectives());
        visitChild(node.getSelectionSet());
        out.br();
    }

    @Override
    public void visitGraphQLVariableDefinition(GraphQLVariableDefinition node) {
        out.append("$" + node.getName());
        out.append(':');
        visit(node.getType());
        if (node.getDefaultValue() != null) {
            out.append(" = ");
            visit(node.getDefaultValue());
        }
    }

    @Override
    public void visitGraphQLNamedType(GraphQLNamedType node) {
        out.append(node.getName());
    }

    @Override
    public void visitGraphQLNonNullType(GraphQLNonNullType node) {
        visit(node.getType());
        out.append('!');
    }

    @Override
    public void visitGraphQLListType(GraphQLListType node) {
        out.append('[');
        visit(node.getType());
        out.append(']');
    }

    @Override
    public void visitGraphQLSelectionSet(GraphQLSelectionSet node) {
        out.append('{');
        out.incIndent();
        for (int i = 0, n = node.size(); i < n; i++) {
            out.indent();
            GraphQLSelection selection = node.getSelections().get(i);
            visit(selection);
        }
        out.decIndent();
        out.indent();
        out.append('}');
    }

    @Override
    public void visitGraphQLFieldSelection(GraphQLFieldSelection node) {
        if (node.getAlias() != null) {
            out.append(node.getAlias());
            out.append(':');
        }
        out.append(node.getName());
        appendArgs(node.getArguments());

        visitChildren(node.getDirectives());
        visitChild(node.getSelectionSet());
    }

    void appendArgs(List<GraphQLArgument> args) {
        if (args != null && !args.isEmpty()) {
            out.append('(');
            out.incIndent();
            for (int i = 0, n = args.size(); i < n; i++) {
                GraphQLArgument arg = args.get(i);
                visitGraphQLArgument(arg);
                if (i != n - 1) {
                    out.append(", ");
                }
            }
            out.decIndent();
            out.append(')');
        }
    }

    @Override
    public void visitGraphQLFragmentSelection(GraphQLFragmentSelection node) {
        out.append("...");
        out.append(node.getFragmentName());
    }

    @Override
    public void visitGraphQLFragment(GraphQLFragment node) {
        out.append("fragment ");
        out.append(node.getName());
        out.append(" on ");
        out.append(node.getOnType());
        visitChild(node.getSelectionSet());
        out.br();
    }

    @Override
    public void visitGraphQLObjectDefinition(GraphQLObjectDefinition node) {
        printDesc(node.getDescription());
        if (node.getExtension()) {
            out.append("extend ");
        }
        out.append("type ");
        out.append(node.getName());
        visitChildren(node.getDirectives());

        out.append('{');
        out.incIndent();
        for (int i = 0, n = node.getFields().size(); i < n; i++) {
            out.indent();
            GraphQLFieldDefinition field = node.getFields().get(i);
            visit(field);
        }
        out.decIndent();
        out.indent();
        out.append("}").br();
    }

    @Override
    public void visitGraphQLFieldDefinition(GraphQLFieldDefinition node) {
        printDesc(node.getDescription());
        out.append(node.getName());
        printArgDefs(node.getArguments());
        out.append(" : ");
        visit(node.getType());
        visitChildren(node.getDirectives());
    }

    @Override
    public void visitGraphQLInputDefinition(GraphQLInputDefinition node) {
        printDesc(node.getDescription());
        if (node.getExtension()) {
            out.append("extend ");
        }
        out.append("input ");
        out.append(node.getName());
        visitChildren(node.getDirectives());

        out.append('{');
        out.incIndent();
        for (int i = 0, n = node.getFields().size(); i < n; i++) {
            out.indent();
            GraphQLInputFieldDefinition field = node.getFields().get(i);
            visit(field);
        }
        out.decIndent();
        out.indent();
        out.append("}").br();
    }

    @Override
    public void visitGraphQLInputFieldDefinition(GraphQLInputFieldDefinition node) {
        printDesc(node.getDescription());
        out.append(node.getName());
        out.append(" : ");
        visit(node.getType());
        if (node.getDefaultValue() != null) {
            out.append(" = ");
            visit(node.getDefaultValue());
        }
        visitChildren(node.getDirectives());
    }

    @Override
    public void visitGraphQLScalarDefinition(GraphQLScalarDefinition node) {
        printDesc(node.getDescription());
        if (node.getExtension()) {
            out.append("extend ");
        }
        out.append("scalar ");
        out.append(node.getName());
        visitChildren(node.getDirectives());
    }

    private void printArgDefs(List<GraphQLArgumentDefinition> args) {
        if (args == null || args.isEmpty())
            return;

        out.append('(');
        for (int i = 0, n = args.size(); i < n; i++) {
            if (i != 0)
                out.append(',');

            visit(args.get(i));
        }
        out.append(')').append(' ');
    }

    @Override
    public void visitGraphQLArgumentDefinition(GraphQLArgumentDefinition node) {
        out.append(node.getName());
        out.append(':');
        visit(node.getType());
        if (node.getDefaultValue() != null) {
            out.append(" = ");
            visit(node.getDefaultValue());
        }
    }

    @Override
    public void visitGraphQLDirectiveDefinition(GraphQLDirectiveDefinition node) {
        out.append("directive ");
        out.append('@');
        out.append(node.getName());
        appendArgDefs(node.getArguments());
        if (node.getRepeatable()) {
            out.append(" repeatable ");
        }
        out.append(" on ");
        out.append(StringHelper.join(node.getLocations(), "|"));
    }

    void appendArgDefs(List<GraphQLArgumentDefinition> args) {
        if (args != null && !args.isEmpty()) {
            out.append('(');
            out.incIndent();
            for (int i = 0, n = args.size(); i < n; i++) {
                GraphQLArgumentDefinition arg = args.get(i);
                visitGraphQLArgumentDefinition(arg);
                if (i != n - 1) {
                    out.append(", ");
                }
            }
            out.decIndent();
            out.append(')');
        }
    }

    @Override
    public void visitGraphQLUnionTypeDefinition(GraphQLUnionTypeDefinition node) {
        printDesc(node.getDescription());
        out.append("union ");
        out.append(node.getName());
        out.append(" = ");
        for (int i = 0, n = node.getTypes().size(); i < n; i++) {
            out.append(node.getTypes().get(i).getName());
            if (i != n - 1)
                out.append('|');
        }
    }

    @Override
    public void visitGraphQLEnumDefinition(GraphQLEnumDefinition node) {
        printDesc(node.getDescription());
        out.append("enum ");
        out.append(node.getName());
        out.append(" {");
        out.incIndent();
        for (GraphQLEnumValueDefinition value : node.getEnumValues()) {
            out.indent();
            visit(value);
        }
        out.decIndent();
        out.indent();
        out.append('}').br();
    }

    @Override
    public void visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node) {
        printDesc(node.getDescription());
        out.append(node.getName());
    }

    private void printDesc(String desc) {
        if (!StringHelper.isEmpty(desc)) {
            out.indent();
            out.append(StringHelper.quote(desc));
        }
        out.indent();
    }
}