/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.resource.component.parse.AbstractCharReaderResourceParser;
import io.nop.graphql.core.GraphQLErrors;
import io.nop.graphql.core.ast.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.commons.CommonErrors.ARG_START_LOC;
import static io.nop.commons.CommonErrors.ERR_SCAN_STRING_NOT_END;
import static io.nop.commons.util.StringHelper.isGraphQLNameStart;
import static io.nop.graphql.core.GraphQLErrors.ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_INVALID_DIRECTIVE_LOCATION;

public class GraphQLDocumentParser extends AbstractCharReaderResourceParser<GraphQLDocument> {

    @Override
    protected GraphQLDocument doParse(SourceLocation loc, ICharReader reader) {
        TextScanner sc = TextScanner.fromReader(loc, reader);
        return document(sc);
    }

    public GraphQLSelectionSet parseSelectionSet(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        GraphQLSelectionSet selectionSet = selectionSet(sc);
        sc.checkEnd();
        return selectionSet;
    }

    private GraphQLDocument document(TextScanner sc) {
        GraphQLDocument doc = new GraphQLDocument();
        doc.setLocation(sc.location());
        sc.skipBlank();
        List<GraphQLDefinition> definitions = new ArrayList<>();
        do {
            skipComments(sc);
            String description = description(sc);
            skipComments(sc);
            if (sc.tryMatchToken("directive")) {
                GraphQLDirectiveDefinition def = directiveDef(sc);
                def.setDescription(description);
                definitions.add(def);
            } else if (sc.tryMatchToken("fragment")) {
                GraphQLFragment fragment = fragment(sc);
                fragment.setDescription(description);
                definitions.add(fragment);
            } else if (sc.tryMatchToken("extend")) {
                GraphQLTypeDefinition def = parseType(sc);
                if (def == null) {
                    sc.matchToken("type"); // 实际会在这一句报错，不会执行到下一句。抛出异常仅仅是为了消除编译警告
                    throw new IllegalStateException("invalid extend syntax");
                }
                def.setExtension(true);
                def.setDescription(description);
                definitions.add(def);
            } else {
                GraphQLTypeDefinition typeDef = parseType(sc);
                if (typeDef != null) {
                    typeDef.setDescription(description);
                    definitions.add(typeDef);
                    continue;
                }
                GraphQLDefinition def = operation(sc);
                if (def == null)
                    break;

                def.setDescription(description);
                definitions.add(def);
            }
        } while (!sc.isEnd());
        sc.checkEnd();

        doc.setDefinitions(definitions);
        return doc;
    }

    private GraphQLTypeDefinition parseType(TextScanner sc) {
        if (sc.tryMatchToken("type")) {
            return objType(sc);
        } else if (sc.tryMatchToken("enum")) {
            return enumDef(sc);
        } else if (sc.tryMatchToken("scalar")) {
            return scalarDef(sc);
        } else if (sc.tryMatchToken("input")) {
            return inputDef(sc);
        } else {
            return null;
        }
    }

    private void skipComments(TextScanner sc) {
        while (sc.tryConsume('#')) {
            sc.skipLine();
            sc.skipBlank();
        }
    }

    private String description(TextScanner sc) {
        if (sc.startsWith("\"\"\"")) {
            SourceLocation loc = sc.location();
            sc.next(3);
            MutableString buf = sc.useBuf();
            while (!sc.isEnd()) {
                if (sc.cur == '\\') {
                    sc.next();
                    if (sc.isEnd())
                        throw sc.newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
                    buf.append((char) sc.cur);
                } else if (sc.cur == '\"') {
                    if (sc.tryMatch("\"\"\"")) {
                        sc.skipBlank();
                        return buf.toString();
                    }
                } else {
                    buf.append((char) sc.cur);
                }
                sc.next();
            }
        } else if (sc.cur == '"') {
            String str = sc.nextJavaString();
            sc.skipBlank();
            return str;
        }
        return null;
    }

    private GraphQLObjectDefinition objType(TextScanner sc) {
        GraphQLObjectDefinition def = new GraphQLObjectDefinition();
        def.setLocation(sc.location());

        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        def.setName(name);

        List<GraphQLDirective> directives = directives(sc);
        def.setDirectives(directives);

        sc.match('{');
        List<GraphQLFieldDefinition> fields = fields(sc);
        sc.match('}');
        def.setFields(fields);
        return def;
    }

    private GraphQLInputDefinition inputDef(TextScanner sc) {
        GraphQLInputDefinition def = new GraphQLInputDefinition();
        def.setLocation(sc.location());

        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        def.setName(name);

        List<GraphQLDirective> directives = directives(sc);
        def.setDirectives(directives);

        sc.match('{');
        List<GraphQLInputFieldDefinition> fields = inputFields(sc);
        sc.match('}');
        def.setFields(fields);
        return def;
    }

    private GraphQLFragment fragment(TextScanner sc) {
        GraphQLFragment fragment = new GraphQLFragment();
        fragment.setLocation(sc.location());
        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        fragment.setName(name);

        sc.matchToken("on");
        String typeName = sc.nextGraphQLVar();
        sc.skipBlank();
        fragment.setOnType(typeName);

        List<GraphQLDirective> directives = directives(sc);
        fragment.setDirectives(directives);

        GraphQLSelectionSet selectionSet = selectionSet(sc);
        fragment.setSelectionSet(selectionSet);
        return fragment;
    }

    private List<GraphQLFieldDefinition> fields(TextScanner sc) {
        List<GraphQLFieldDefinition> ret = new ArrayList<>();
        while (sc.cur != '}' && !sc.isEnd()) {
            skipComments(sc);
            GraphQLFieldDefinition field = fieldDef(sc);
            ret.add(field);
            sc.skipBlank();
            sc.tryMatch(',');
        }
        return ret;
    }

    private List<GraphQLInputFieldDefinition> inputFields(TextScanner sc) {
        List<GraphQLInputFieldDefinition> ret = new ArrayList<>();
        while (sc.cur != '}' && !sc.isEnd()) {
            skipComments(sc);
            GraphQLInputFieldDefinition field = inputFieldDef(sc);
            ret.add(field);
            sc.skipBlank();
            sc.tryMatch(',');
        }
        return ret;
    }

    private GraphQLFieldDefinition fieldDef(TextScanner sc) {
        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setLocation(sc.location());

        skipComments(sc);
        String description = description(sc);
        field.setDescription(description);
        skipComments(sc);

        String name = sc.nextGraphQLVar();
        field.setName(name);
        sc.skipBlank();
        if (sc.cur == '(') {
            field.setArguments(argumentDefinitions(sc));
        }
        sc.match(':');
        GraphQLType type = type(sc);
        field.setType(type);

        List<GraphQLDirective> directives = directives(sc);
        field.setDirectives(directives);
        return field;
    }

    private GraphQLInputFieldDefinition inputFieldDef(TextScanner sc) {
        GraphQLInputFieldDefinition field = new GraphQLInputFieldDefinition();
        field.setLocation(sc.location());

        skipComments(sc);
        String description = description(sc);
        field.setDescription(description);
        skipComments(sc);

        String name = sc.nextGraphQLVar();
        field.setName(name);
        sc.skipBlank();
        sc.match(':');
        GraphQLType type = type(sc);
        field.setType(type);

        if (sc.tryMatch('=')) {
            GraphQLValue value = value(sc);
            field.setDefaultValue(value);
        }

        List<GraphQLDirective> directives = directives(sc);
        field.setDirectives(directives);
        return field;
    }


    private GraphQLDirectiveDefinition directiveDef(TextScanner sc) {
        GraphQLDirectiveDefinition ret = new GraphQLDirectiveDefinition();
        ret.setLocation(sc.location());

        sc.match('@');
        String name = sc.nextGraphQLVar();
        ret.setName(name);
        sc.skipBlank();
        if (sc.cur == '(') {
            ret.setArguments(argumentDefinitions(sc));
        }
        if (sc.tryMatchToken("repeatable")) {
            ret.setRepeatable(true);
        }

        sc.matchToken("on");

        List<GraphQLDirectiveLocation> locations = directiveLocations(sc);
        ret.setLocations(locations);
        return ret;
    }

    private List<GraphQLDirectiveLocation> directiveLocations(TextScanner sc) {
        List<GraphQLDirectiveLocation> ret = new ArrayList<>();
        ret.add(directiveLocation(sc));
        while (sc.tryMatch('|')) {
            ret.add(directiveLocation(sc));
        }
        return ret;
    }

    GraphQLDirectiveLocation directiveLocation(TextScanner sc) {
        String name = sc.nextGraphQLVar();
        GraphQLDirectiveLocation loc = GraphQLDirectiveLocation.fromText(name);
        if (loc == null)
            throw sc.newError(ERR_GRAPHQL_INVALID_DIRECTIVE_LOCATION).param(ARG_NAME, name);
        sc.skipBlank();
        return loc;
    }

    private GraphQLOperation operation(TextScanner sc) {
        GraphQLOperation op = new GraphQLOperation();
        op.setLocation(sc.location());

        GraphQLOperationType type = GraphQLOperationType.query;
        if (sc.tryMatchToken(GraphQLOperationType.query.name())) {
            operationVars(sc, op);
        } else if (sc.tryMatchToken(GraphQLOperationType.mutation.name())) {
            type = GraphQLOperationType.mutation;
            operationVars(sc, op);
        } else if (sc.tryMatchToken(GraphQLOperationType.subscription.name())) {
            type = GraphQLOperationType.subscription;
            operationVars(sc, op);
        } else if (sc.cur == '{') {
            type = GraphQLOperationType.query;
        } else {
            return null;
        }
        op.setOperationType(type);
        GraphQLSelectionSet selectionSet = selectionSet(sc);
        op.setSelectionSet(selectionSet);
        return op;
    }

    private void operationVars(TextScanner sc, GraphQLOperation op) {
        if (isGraphQLNameStart(sc.cur)) {
            String name = sc.nextGraphQLVar();
            sc.skipBlank();
            op.setName(name);
        }

        List<GraphQLVariableDefinition> vars = variableDefinitions(sc);
        op.setVariableDefinitions(vars);

        if (sc.cur == '@') {
            List<GraphQLDirective> directives = directives(sc);
            op.setDirectives(directives);
        }
    }

    private List<GraphQLVariableDefinition> variableDefinitions(TextScanner sc) {
        if (!sc.tryMatch('('))
            return Collections.emptyList();
        if (sc.tryMatch(')'))
            return Collections.emptyList();

        List<GraphQLVariableDefinition> ret = new ArrayList<>();
        do {
            GraphQLVariableDefinition var = variableDefinition(sc);
            ret.add(var);
        } while (sc.tryMatch(','));

        sc.match(')');

        return ret;
    }

    private GraphQLVariableDefinition variableDefinition(TextScanner sc) {
        GraphQLVariableDefinition var = new GraphQLVariableDefinition();
        var.setLocation(sc.location());

        sc.consume('$');
        String name = sc.nextGraphQLVar();

        var.setName(name);
        sc.match(':');
        GraphQLType type = type(sc);
        var.setType(type);

        if (sc.tryMatch('=')) {
            GraphQLValue value = value(sc);
            var.setDefaultValue(value);
        }
        List<GraphQLDirective> directives = directives(sc);
        var.setDirectives(directives);
        return var;
    }

    public GraphQLType parseType(SourceLocation loc, String type) {
        return type(TextScanner.fromString(loc, type));
    }

    private GraphQLType type(TextScanner sc) {
        SourceLocation loc = sc.location();
        GraphQLType ret;
        if (sc.tryMatch('[')) {
            GraphQLListType type = new GraphQLListType();
            type.setLocation(loc);
            type.setType(type(sc));
            sc.match(']');
            ret = type;
        } else {
            String typeName = sc.nextGraphQLVar();
            sc.skipBlank();
            GraphQLNamedType type = new GraphQLNamedType();
            type.setLocation(loc);
            type.setName(typeName);
            ret = type;
        }
        if (sc.tryMatch('!')) {
            GraphQLNonNullType nonNullType = new GraphQLNonNullType();
            nonNullType.setLocation(loc);
            nonNullType.setType(ret);
            ret = nonNullType;
        }
        return ret;
    }

    private List<GraphQLDirective> directives(TextScanner sc) {
        if (sc.cur != '@')
            return Collections.emptyList();

        List<GraphQLDirective> ret = new ArrayList<>();
        do {
            GraphQLDirective directive = directive(sc);
            ret.add(directive);
        } while (sc.cur == '@');
        return ret;
    }

    private GraphQLDirective directive(TextScanner sc) {
        GraphQLDirective directive = new GraphQLDirective();
        directive.setLocation(sc.location());

        sc.consume('@');
        String name = sc.nextGraphQLVar();
        List<GraphQLArgument> arguments = arguments(sc);

        directive.setName(name);
        directive.setArguments(arguments);
        return directive;
    }

    private List<GraphQLArgumentDefinition> argumentDefinitions(TextScanner sc) {
        if (!sc.tryMatch('('))
            return Collections.emptyList();
        if (sc.tryMatch(')'))
            return Collections.emptyList();

        List<GraphQLArgumentDefinition> ret = new ArrayList<>();
        do {
            GraphQLArgumentDefinition arg = argumentDefinition(sc);
            ret.add(arg);
        } while (sc.tryMatch(','));

        sc.match(')');
        return ret;
    }

    private GraphQLArgumentDefinition argumentDefinition(TextScanner sc) {
        GraphQLArgumentDefinition arg = new GraphQLArgumentDefinition();
        arg.setLocation(sc.location());

        String name = sc.nextGraphQLVar();
        sc.match(':');
        GraphQLType type = type(sc);
        sc.skipBlank();

        arg.setName(name);
        arg.setType(type);

        if (sc.tryMatch('=')) {
            GraphQLValue defaultValue = value(sc);
            arg.setDefaultValue(defaultValue);
        }
        return arg;
    }

    private List<GraphQLArgument> arguments(TextScanner sc) {
        if (!sc.tryMatch('('))
            return Collections.emptyList();
        if (sc.tryMatch(')'))
            return Collections.emptyList();

        List<GraphQLArgument> ret = new ArrayList<>();
        do {
            GraphQLArgument arg = argument(sc);
            ret.add(arg);
        } while (sc.tryMatch(','));

        sc.match(')');
        return ret;
    }

    private GraphQLArgument argument(TextScanner sc) {
        GraphQLArgument arg = new GraphQLArgument();
        arg.setLocation(sc.location());

        String name = sc.nextGraphQLVar();
        sc.match(':');
        GraphQLValue value = value(sc);
        arg.setName(name);
        arg.setValue(value);
        sc.skipBlank();
        return arg;
    }

    private GraphQLValue value(TextScanner sc) {
        SourceLocation loc = sc.location();
        if (sc.cur == '$') {
            sc.next();
            String name = sc.nextGraphQLVar();
            sc.skipBlank();
            return GraphQLVariable.valueOf(loc, name);
        }
        if (sc.tryMatchToken("null")) {
            return GraphQLLiteral.valueOf(loc, null);
        }
        if (sc.tryMatchToken("true"))
            return GraphQLLiteral.valueOf(loc, true);
        if (sc.tryMatchToken("false"))
            return GraphQLLiteral.valueOf(loc, false);
        if (sc.cur == '"') {
            String str = sc.nextJavaString();
            sc.skipBlank();
            return GraphQLLiteral.valueOf(loc, str);
        }
        if (sc.maybeNumber()) {
            Number num = sc.nextNumber();
            sc.skipBlank();
            return GraphQLLiteral.valueOf(loc, num);
        }
        if (sc.cur == '[') {
            return arrayValue(sc);
        }
        if (sc.cur == '{')
            return objectValue(sc);
        throw sc.newError(GraphQLErrors.ERR_GRAPHQL_PARSE_UNEXPECTED_CHAR);
    }

    private GraphQLArrayValue arrayValue(TextScanner sc) {
        GraphQLArrayValue array = new GraphQLArrayValue();
        array.setLocation(sc.location());
        sc.match('[');
        if (sc.tryMatch(']')) {
            array.setItems(Collections.emptyList());
            return array;
        }

        List<GraphQLValue> ret = new ArrayList<>();

        do {
            if (sc.tryMatch(']'))
                break;
            GraphQLValue value = value(sc);
            ret.add(value);
        } while (sc.tryMatch(','));
        return array;
    }

    private GraphQLObjectValue objectValue(TextScanner sc) {
        GraphQLObjectValue object = new GraphQLObjectValue();
        object.setLocation(sc.location());
        sc.match('{');
        if (sc.tryMatch('}')) {
            object.setProperties(Collections.emptyList());
            return object;
        }

        List<GraphQLPropertyValue> ret = new ArrayList<>();
        while (sc.cur != '}' && !sc.isEnd()) {
            skipComments(sc);
            GraphQLPropertyValue prop = property(sc);
            ret.add(prop);
            sc.skipBlank();
            sc.tryMatch(',');
        }
        sc.match('}');

        object.setProperties(ret);

        return object;
    }

    GraphQLPropertyValue property(TextScanner sc) {
        GraphQLPropertyValue prop = new GraphQLPropertyValue();
        prop.setLocation(sc.location());
        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        sc.match(':');
        GraphQLValue value = value(sc);

        prop.setName(name);
        prop.setValue(value);
        return prop;
    }

    private GraphQLSelectionSet selectionSet(TextScanner sc) {
        GraphQLSelectionSet selectionSet = new GraphQLSelectionSet();
        selectionSet.setLocation(sc.location());

        sc.match('{');
        List<GraphQLSelection> selections = selections(sc);
        sc.match('}');

        selectionSet.setSelections(selections);

        return selectionSet;
    }

    private List<GraphQLSelection> selections(TextScanner sc) {
        if (sc.cur == '}')
            return Collections.emptyList();

        List<GraphQLSelection> ret = new ArrayList<>();
        do {
            GraphQLSelection sel = selection(sc);
            ret.add(sel);
            sc.tryMatch(',');
        } while (sc.cur != '}' && !sc.isEnd());
        return ret;
    }

    private GraphQLSelection selection(TextScanner sc) {
        skipComments(sc);
        if (sc.tryMatch("...")) {
            return fragmentSelection(sc);
        } else {
            return fieldSelection(sc);
        }
    }

    private GraphQLSelection fragmentSelection(TextScanner sc) {
        GraphQLFragmentSelection sel = new GraphQLFragmentSelection();
        sel.setLocation(sc.location());
        String fragmentName = sc.nextGraphQLVar();
        sc.skipBlank();
        sel.setFragmentName(fragmentName);
        sel.setDirectives(directives(sc));
        return sel;
    }

    private GraphQLFieldSelection fieldSelection(TextScanner sc) {
        GraphQLFieldSelection sel = new GraphQLFieldSelection();
        sel.setLocation(sc.location());

        skipComments(sc);
        String alias = null;
        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        if (sc.tryMatch(':')) {
            alias = name;
            name = sc.nextGraphQLVar();
            sc.skipBlank();
        }
        sel.setAlias(alias);
        sel.setName(name);
        List<GraphQLArgument> args = arguments(sc);
        sel.setArguments(args);

        sel.setDirectives(directives(sc));

        if (sc.cur == '{') {
            GraphQLSelectionSet selectionSet = selectionSet(sc);
            sel.setSelectionSet(selectionSet);
        }
        return sel;
    }

    private GraphQLScalarDefinition scalarDef(TextScanner sc) {
        GraphQLScalarDefinition def = new GraphQLScalarDefinition();
        def.setLocation(sc.location());
        skipComments(sc);
        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        def.setName(name);
        def.setDirectives(directives(sc));
        return def;
    }

    private GraphQLEnumDefinition enumDef(TextScanner sc) {
        GraphQLEnumDefinition def = new GraphQLEnumDefinition();
        def.setLocation(sc.location());
        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        def.setName(name);
        def.setDirectives(directives(sc));
        sc.match('{');

        List<GraphQLEnumValueDefinition> values = new ArrayList<>();
        while (sc.cur != '}' && !sc.isEnd()) {
            skipComments(sc);
            GraphQLEnumValueDefinition value = enumValue(sc);
            values.add(value);
            sc.skipBlank();
            sc.tryMatch(',');
        }
        def.setEnumValues(values);

        sc.match('}');
        return def;
    }

    private GraphQLEnumValueDefinition enumValue(TextScanner sc) {
        GraphQLEnumValueDefinition def = new GraphQLEnumValueDefinition();
        def.setLocation(sc.location());

        skipComments(sc);
        String description = description(sc);
        def.setDescription(description);
        skipComments(sc);

        String name = sc.nextGraphQLVar();
        sc.skipBlank();
        def.setName(name);

        def.setDirectives(directives(sc));

        return def;
    }
}