package io.nop.xlang.expr.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.ObjectTypeDef;
import io.nop.xlang.ast.PropertyTypeDef;
import io.nop.xlang.ast.TypeAliasDeclaration;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.TypeNode;

import java.util.ArrayList;
import java.util.List;

public class TypeDefinitionParser extends SimpleExprParser {

    public TypeAliasDeclaration parseTypeDefinition(String source) {
        return parseTypeDefinition(SourceLocation.fromPath("type-definition"), source);
    }

    public TypeAliasDeclaration parseTypeDefinition(SourceLocation loc, String source) {
        TextScanner sc = TextScanner.fromString(loc, source);
        sc.useEvalException = isUseEvalException();

        TypeAliasDeclaration declaration = parseTypeAliasDeclaration(sc);

        sc.checkEnd();
        return declaration;
    }

    protected TypeAliasDeclaration parseTypeAliasDeclaration(TextScanner sc) {
        SourceLocation loc = sc.location();

        String leadingComment = collectLeadingComment(sc);

        sc.matchToken("type");
        String typeName = sc.nextJavaVar();
        sc.skipBlank();

        sc.match('=');
        sc.skipBlank();

        TypeNode defType = parseStructuredTypeDef(sc);

        TypeAliasDeclaration declaration = new TypeAliasDeclaration();
        declaration.setLocation(loc);
        declaration.setTypeName(Identifier.valueOf(loc, internToken(typeName)));
        declaration.setDefType(defType);

        if (!StringHelper.isEmpty(leadingComment)) {
            declaration.setLeadingComment(leadingComment);
        }

        return declaration;
    }

    protected TypeNode parseStructuredTypeDef(TextScanner sc) {
        if (sc.cur == '{') {
            return parseObjectTypeDef(sc);
        }

        return parseNamedTypeNode(sc);
    }

    protected ObjectTypeDef parseObjectTypeDef(TextScanner sc) {
        SourceLocation loc = sc.location();

        sc.match('{');
        sc.skipBlank();

        List<PropertyTypeDef> properties = new ArrayList<>();

        while (sc.cur != '}') {
            String leadingComment = collectLeadingComment(sc);

            PropertyTypeDef prop = parsePropertyTypeDef(sc);
            if (!StringHelper.isEmpty(leadingComment)) {
                prop.setLeadingComment(leadingComment);
            }

            properties.add(prop);

            if (!sc.tryMatch(',')) {
                break;
            }
            sc.skipBlank();
        }

        sc.match('}');

        ObjectTypeDef objectTypeDef = new ObjectTypeDef();
        objectTypeDef.setLocation(loc);
        objectTypeDef.setTypes(properties);

        return objectTypeDef;
    }

    protected PropertyTypeDef parsePropertyTypeDef(TextScanner sc) {
        SourceLocation loc = sc.location();

        String name = sc.nextJavaVar();
        sc.skipBlank();

        boolean optional = false;
        if (sc.cur == '?') {
            optional = true;
            sc.next();
            sc.skipBlank();
        }

        TypeNode valueType = null;
        if (sc.cur == ':') {
            sc.next();
            sc.skipBlank();
            valueType = parseType(sc);
        } else {
            valueType = TypeNameNode.valueOf(loc, internToken("any"));
        }

        PropertyTypeDef prop = new PropertyTypeDef();
        prop.setLocation(loc);
        prop.setName(internToken(name));
        prop.setOptional(optional);
        prop.setValueType(valueType);

        return prop;
    }

    protected TypeNode parseType(TextScanner sc) {
        return parseNamedTypeNode(sc);
    }

    protected TypeNameNode parseNamedTypeNode(TextScanner sc) {
        SourceLocation loc = sc.location();
        String typeName = sc.nextJavaPropPath();
        sc.skipBlank();

        return TypeNameNode.valueOf(loc, internToken(typeName));
    }

    protected String collectLeadingComment(TextScanner sc) {
        MutableString commentBuffer = new MutableString();

        sc.skipBlank();
        boolean hasComment = false;

        while (true) {
            if (sc.cur == '/' && sc.peek() == '/') {
                sc.next(2);
                skipLineAndCollect(sc, commentBuffer);
                hasComment = true;
                sc.skipBlank();
            } else if (sc.cur == '/' && sc.peek() == '*') {
                sc.next(2);
                skipBlockCommentAndCollect(sc, commentBuffer);
                hasComment = true;
                sc.skipBlank();
            } else {
                break;
            }
        }

        return hasComment ? commentBuffer.trim().toString() : null;
    }

    private void skipLineAndCollect(TextScanner sc, MutableString buffer) {
        while (!sc.isEnd() && sc.cur != '\n' && sc.cur != '\r') {
            buffer.append((char) sc.cur);
            sc.next();
        }
        buffer.append('\n');
        sc.skipLine(null);
    }

    private void skipBlockCommentAndCollect(TextScanner sc, MutableString buffer) {
        while (!sc.isEnd()) {
            if (sc.cur == '*' && sc.peek() == '/') {
                sc.next(2);
                break;
            }
            buffer.append((char) sc.cur);
            sc.next();
        }
    }
}
