package io.nop.xlang.expr.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.ArrayTypeNode;
import io.nop.xlang.ast.FunctionArgTypeDef;
import io.nop.xlang.ast.FunctionTypeDef;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IntersectionTypeDef;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.ObjectTypeDef;
import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.PropertyTypeDef;
import io.nop.xlang.ast.TupleTypeDef;
import io.nop.xlang.ast.TypeAliasDeclaration;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.TypeNode;
import io.nop.xlang.ast.UnionTypeDef;

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
        // Parse the first type (could be object type or other types)
        TypeNode type = parsePrimaryType(sc);

        // Parse union and intersection operators (left-associative)
        type = parseUnionOrIntersectionType(sc, type);

        return type;
    }

    /**
     * Parse union (|) and intersection (&) operators with left-associative parsing
     */
    protected TypeNode parseUnionOrIntersectionType(TextScanner sc, TypeNode left) {
        sc.skipBlank();

        // First, check for union operators (|)
        while (sc.cur == '|') {
            SourceLocation loc = sc.location();
            sc.next(); // consume '|'
            sc.skipBlank();

            TypeNode right = parsePrimaryType(sc);
            left = buildUnionType(loc, left, right);

            sc.skipBlank();
        }

        // Then, check for intersection operators (&)
        // In TypeScript, intersection binds tighter than union, so we parse it after union
        // But for simplicity, we'll parse them left-to-right
        while (sc.cur == '&') {
            SourceLocation loc = sc.location();
            sc.next(); // consume '&'
            sc.skipBlank();

            TypeNode right = parsePrimaryType(sc);
            left = buildIntersectionType(loc, left, right);

            sc.skipBlank();
        }

        return left;
    }

    /**
     * Parse primary types: object type, tuple, function, or named type
     */
    protected TypeNode parsePrimaryType(TextScanner sc) {
        sc.skipBlank();

        if (sc.cur == '{') {
            return parseObjectTypeDef(sc);
        } else if (sc.cur == '[') {
            return parseTupleType(sc);
        } else if (sc.cur == '(') {
            // Could be function type or parenthesized type
            return parseFunctionOrParenthesizedType(sc);
        } else {
            return parseNamedTypeWithModifiers(sc);
        }
    }

    /**
     * Parse named types with optional array suffix and type parameters
     * e.g., "string", "int[]", "List<string>", "Map<string, number>"
     */
    protected NamedTypeNode parseNamedTypeWithModifiers(TextScanner sc) {
        SourceLocation loc = sc.location();
        String typeName = parseTypeName(sc);

        // Check for parameterized type: Name<Type1, Type2>
        if (sc.cur == '<') {
            return parseParameterizedType(sc, loc, typeName);
        }

        // Check for array suffix: []
        NamedTypeNode typeNode = TypeNameNode.valueOf(loc, internToken(typeName));
        while (sc.cur == '[') {
            sc.next();
            sc.skipBlank();
            sc.match(']');
            sc.skipBlank();

            ArrayTypeNode arrayNode = new ArrayTypeNode();
            arrayNode.setLocation(loc);
            arrayNode.setComponentType(typeNode);
            typeNode = arrayNode;
        }

        return typeNode;
    }

    /**
     * Parse a type name (identifier) that stops at special characters
     */
    protected String parseTypeName(TextScanner sc) {
        StringBuilder sb = new StringBuilder();

        while (!isEndOfTypeName(sc.cur)) {
            sb.append((char) sc.cur);
            sc.next();
        }

        return sb.toString();
    }

    /**
     * Check if current character marks the end of a type name
     */
    protected boolean isEndOfTypeName(int ch) {
        return ch <= 0 || ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
                || ch == '|' || ch == '&' || ch == '[' || ch == ']' || ch == '(' || ch == ')'
                || ch == '<' || ch == '>' || ch == ',' || ch == '=' || ch == ':'
                || ch == '?' || ch == '{' || ch == '}';
    }

    /**
     * Parse parameterized type: Name<Type1, Type2>
     */
    protected ParameterizedTypeNode parseParameterizedType(TextScanner sc, SourceLocation loc, String typeName) {
        sc.match('<');
        sc.skipBlank();

        List<NamedTypeNode> typeArgs = new ArrayList<>();

        while (sc.cur != '>') {
            NamedTypeNode typeArg = parseNamedTypeWithModifiers(sc);
            typeArgs.add(typeArg);

            if (!sc.tryMatch(',')) {
                break;
            }
            sc.skipBlank();
        }

        sc.match('>');
        sc.skipBlank();

        ParameterizedTypeNode paramNode = new ParameterizedTypeNode();
        paramNode.setLocation(loc);
        paramNode.setTypeName(internToken(typeName));
        paramNode.setTypeArgs(typeArgs);

        return paramNode;
    }

    /**
     * Parse tuple type: [Type1, Type2, ...]
     */
    protected TupleTypeDef parseTupleType(TextScanner sc) {
        SourceLocation loc = sc.location();
        sc.match('[');
        sc.skipBlank();

        List<TypeNode> types = new ArrayList<>();

        while (sc.cur != ']') {
            TypeNode type = parsePrimaryType(sc);
            types.add(type);

            if (!sc.tryMatch(',')) {
                break;
            }
            sc.skipBlank();
        }

        sc.match(']');
        sc.skipBlank();

        TupleTypeDef tupleNode = new TupleTypeDef();
        tupleNode.setLocation(loc);
        tupleNode.setTypes(types);

        return tupleNode;
    }

    /**
     * Parse function argument: argName: Type or just Type
     */
    protected FunctionArgTypeDef parseFunctionArg(TextScanner sc) {
        SourceLocation loc = sc.location();

        String argName = parseTypeName(sc);
        sc.skipBlank();

        NamedTypeNode argType = null;
        if (sc.cur == ':') {
            sc.next(); // consume ':'
            sc.skipBlank();
            argType = parseNamedTypeWithModifiers(sc);
        } else {
            // No explicit type, assume the name was the type
            argType = TypeNameNode.valueOf(loc, internToken(argName));
            argName = null;
        }

        FunctionArgTypeDef arg = new FunctionArgTypeDef();
        arg.setLocation(loc);
        if (argName != null) {
            arg.setArgName(Identifier.valueOf(loc, internToken(argName)));
        }
        arg.setArgType(argType);

        return arg;
    }

    /**
     * Parse function type or parenthesized type: (args) => ReturnType or (Type)
     */
    protected TypeNode parseFunctionOrParenthesizedType(TextScanner sc) {
        SourceLocation loc = sc.location();
        sc.match('(');
        sc.skipBlank();

        // Parse potential function arguments
        List<FunctionArgTypeDef> args = new ArrayList<>();

        while (sc.cur != ')') {
            FunctionArgTypeDef arg = parseFunctionArg(sc);
            args.add(arg);

            if (!sc.tryMatch(',')) {
                break;
            }
            sc.skipBlank();
        }

        sc.match(')');
        sc.skipBlank();

        // Check if this is a function type: look for '=>'
        if (sc.cur == '=' && sc.peek() == '>') {
            // It's a function type
            sc.match('=');
            sc.match('>');
            sc.skipBlank();

            NamedTypeNode returnType = parseNamedTypeWithModifiers(sc);

            FunctionTypeDef functionNode = new FunctionTypeDef();
            functionNode.setLocation(loc);
            functionNode.setArgs(args);
            functionNode.setReturnType(returnType);

            return functionNode;
        } else {
            // It's a parenthesized type - just return the first arg as the type
            if (args.size() == 1 && args.get(0).getArgName() == null) {
                // Only had a type, no name - return the type
                return args.get(0).getArgType();
            } else {
                // Had named args - this is weird for a parenthesized type
                // For now, create a function with void return as fallback
                FunctionTypeDef functionNode = new FunctionTypeDef();
                functionNode.setLocation(loc);
                functionNode.setArgs(args);
                functionNode.setReturnType(TypeNameNode.valueOf(loc, internToken("void")));

                return functionNode;
            }
        }
    }
 
    /**
     * Build a union type node from two types
     */
    protected UnionTypeDef buildUnionType(SourceLocation loc, TypeNode left, TypeNode right) {
        return buildCompositeType(loc, left, right, UnionTypeDef.class, "union");
    }

    /**
     * Build an intersection type node from two types
     */
    protected IntersectionTypeDef buildIntersectionType(SourceLocation loc, TypeNode left, TypeNode right) {
        return buildCompositeType(loc, left, right, IntersectionTypeDef.class, "intersection");
    }

    /**
     * Build a composite type (union or intersection) from two types
     * @param <T> the composite type (UnionTypeDef or IntersectionTypeDef)
     * @param loc source location
     * @param left left type
     * @param right right type
     * @param compositeClass the class of the composite type
     * @param typeName "union" or "intersection" for error messages
     * @return the composite type node
     */
    @SuppressWarnings("unchecked")
    protected <T extends TypeNode> T buildCompositeType(SourceLocation loc, TypeNode left, TypeNode right,
                                                          Class<T> compositeClass, String typeName) {
        boolean isLeftComposite = compositeClass.isInstance(left);
        boolean isRightComposite = compositeClass.isInstance(right);

        if (isLeftComposite) {
            // Left is already a composite type, add right to it
            T composite = (T) left;
            if (isRightComposite) {
                // Both are composite, merge them
                if (composite instanceof UnionTypeDef) {
                    ((UnionTypeDef) composite).getTypes().addAll(((UnionTypeDef) right).getTypes());
                } else if (composite instanceof IntersectionTypeDef) {
                    ((IntersectionTypeDef) composite).getTypes().addAll(((IntersectionTypeDef) right).getTypes());
                }
            } else if (right instanceof NamedTypeNode) {
                // Add the named type to the composite
                if (composite instanceof UnionTypeDef) {
                    ((UnionTypeDef) composite).makeTypes().add((NamedTypeNode) right);
                } else if (composite instanceof IntersectionTypeDef) {
                    ((IntersectionTypeDef) composite).makeTypes().add((NamedTypeNode) right);
                }
            } else {
                throw new UnsupportedOperationException(
                    "Complex " + typeName + " type not fully supported yet: " + right.getClass().getSimpleName());
            }
            return composite;
        } else {
            // Create a new composite
            T composite;
            try {
                composite = compositeClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create " + typeName + " type", e);
            }
            composite.setLocation(loc);
            List<NamedTypeNode> types = new ArrayList<>();

            if (left instanceof NamedTypeNode) {
                types.add((NamedTypeNode) left);
            } else if (left instanceof UnionTypeDef || left instanceof IntersectionTypeDef) {
                if (left instanceof UnionTypeDef) {
                    types.addAll(((UnionTypeDef) left).getTypes());
                } else {
                    types.addAll(((IntersectionTypeDef) left).getTypes());
                }
            } else {
                throw new UnsupportedOperationException(
                    "Complex " + typeName + " type not fully supported yet: " + left.getClass().getSimpleName());
            }

            if (right instanceof NamedTypeNode) {
                types.add((NamedTypeNode) right);
            } else {
                throw new UnsupportedOperationException(
                    "Complex " + typeName + " type not fully supported yet: " + right.getClass().getSimpleName());
            }

            if (composite instanceof UnionTypeDef) {
                ((UnionTypeDef) composite).setTypes(types);
            } else if (composite instanceof IntersectionTypeDef) {
                ((IntersectionTypeDef) composite).setTypes(types);
            }

            return composite;
        }
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
