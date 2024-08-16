/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.janino;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.javac.JavaCompilerErrors;
import io.nop.xlang.ast.ClassDefinition;
import io.nop.xlang.ast.CompilationUnit;
import io.nop.xlang.ast.Declaration;
import io.nop.xlang.ast.EnumDeclaration;
import io.nop.xlang.ast.EnumMember;
import io.nop.xlang.ast.FieldDeclaration;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.ast.Statement;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangClassKind;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将Java的抽象语法树转换为XLang的AST
 */
public class JavaToXLangTransformer {
    private final boolean ignoreInvalidSyntax;

    public JavaToXLangTransformer(boolean ignoreInvalidSyntax) {
        this.ignoreInvalidSyntax = ignoreInvalidSyntax;
    }

    public JavaToXLangTransformer() {
        this(false);
    }

    public Program buildProgram(Java.CompilationUnit unit) {
        Program program = new Program();
        program.setLocation(SourceLocation.fromPath(unit.fileName));

        List<XLangASTNode> body = new ArrayList<>();
        for (Java.AbstractCompilationUnit.ImportDeclaration decl : unit.importDeclarations) {
            body.add(buildImportDeclaration(decl));
        }

        for (Java.PackageMemberTypeDeclaration type : unit.packageMemberTypeDeclarations) {
            body.add(buildDeclaration(type));
        }

        program.setBody(body);
        return program;
    }

    public CompilationUnit buildCompilationUnit(Java.CompilationUnit unit) {
        CompilationUnit ret = new CompilationUnit();
        ret.setLocation(SourceLocation.fromPath(unit.fileName));

        if (unit.packageDeclaration != null)
            ret.setPackageName(unit.packageDeclaration.packageName);

        List<Statement> statements = new ArrayList<>();
        for (Java.AbstractCompilationUnit.ImportDeclaration decl : unit.importDeclarations) {
            statements.add(buildImportDeclaration(decl));
        }

        for (Java.PackageMemberTypeDeclaration type : unit.packageMemberTypeDeclarations) {
            statements.add(buildDeclaration(type));
        }
        ret.setStatements(statements);
        return ret;
    }

    protected String buildFilePath(String path) {
        return path;
    }

    private SourceLocation buildLocation(Java.Locatable node) {
        Location loc = node.getLocation();
        if (loc == null)
            return null;
        String path = buildFilePath(loc.getFileName());
        return SourceLocation.fromLine(path, loc.getLineNumber(), loc.getColumnNumber());
    }

    public ImportAsDeclaration buildImportDeclaration(Java.AbstractCompilationUnit.ImportDeclaration decl) {
        if (decl instanceof Java.AbstractCompilationUnit.SingleTypeImportDeclaration) {
            return buildImportAsDeclaration((Java.AbstractCompilationUnit.SingleTypeImportDeclaration) decl);
        } else if (decl instanceof Java.AbstractCompilationUnit.SingleStaticImportDeclaration) {
            return buildImportStaticDeclaration((Java.AbstractCompilationUnit.SingleStaticImportDeclaration) decl);
        } else {
            if (ignoreInvalidSyntax)
                return new ImportAsDeclaration();
            throw newTransformError(decl, XLangASTKind.ImportAsDeclaration);
        }
    }

    private NopException newTransformError(Java.Locatable node, XLangASTKind kind) {
        throw new NopException(JavaCompilerErrors.ERR_JAVAC_NOT_SUPPORT_TRANSFORM_TO_XLANG_AST_FAIL)
                .param(JavaCompilerErrors.ARG_JAVA_TYPE, node.getClass().getSimpleName())
                .param(JavaCompilerErrors.ARG_AST_KIND, kind);
    }

    public ImportAsDeclaration buildImportAsDeclaration(Java.AbstractCompilationUnit.SingleTypeImportDeclaration decl) {
        SourceLocation loc = buildLocation(decl);

        QualifiedName source = QualifiedName.valueOf(loc, StringHelper.joinArray(decl.identifiers, "."));
        return XLangASTBuilder.importClass(loc, source, null);
    }

    public ImportAsDeclaration buildImportStaticDeclaration(Java.AbstractCompilationUnit.SingleStaticImportDeclaration decl) {
        SourceLocation loc = buildLocation(decl);

        QualifiedName source = QualifiedName.valueOf(loc, StringHelper.joinArray(decl.identifiers, "."));
        ImportAsDeclaration ret = XLangASTBuilder.importClass(loc, source, null);
        ret.setStaticImport(true);
        return ret;
    }

    public Declaration buildDeclaration(Java.PackageMemberTypeDeclaration type) {
        if (type instanceof Java.PackageMemberEnumDeclaration) {
            return buildEnumDeclaration((Java.PackageMemberEnumDeclaration) type);
        } else if (type instanceof Java.PackageMemberClassDeclaration) {
            return buildClassDeclaration((Java.PackageMemberClassDeclaration) type);
        } else if (type instanceof Java.PackageMemberInterfaceDeclaration) {
            return buildInterfaceDeclaration((Java.PackageMemberInterfaceDeclaration) type);
        } else {
            if (ignoreInvalidSyntax)
                return new ClassDefinition();
            throw newTransformError(type, XLangASTKind.ClassDefinition);
        }
    }

    public ClassDefinition buildClassDeclaration(Java.PackageMemberClassDeclaration type) {
        SourceLocation loc = buildLocation(type);

        ClassDefinition def = new ClassDefinition();
        def.setLocation(loc);
        def.setClassKind(XLangClassKind.CLASS);
        def.setName(Identifier.valueOf(loc, type.getName()));
        ParameterizedTypeNode pType = buildType(type.extendedType);
        def.setExtendsType(pType);
        def.setImplementTypes(buildTypes(type.implementedTypes));
        List<FieldDeclaration> fields = buildFieldDeclarations(type.getMemberTypeDeclarations());
        def.setFields(fields);
        List<FunctionDeclaration> methods = buildFunctionDeclarations(type.getMethodDeclarations());
        def.setMethods(methods);
        return def;
    }

    public ParameterizedTypeNode buildType(Java.Type type) {
        if (type == null)
            return null;
        SourceLocation loc = buildLocation(type);
        ParameterizedTypeNode ret = new ParameterizedTypeNode();
        ret.setLocation(loc);

        if (type instanceof Java.ReferenceType) {
            Java.ReferenceType refType = (Java.ReferenceType) type;
            ret.setTypeName(StringHelper.joinArray(refType.identifiers, "."));
            if (refType.typeArguments != null) {
                List<NamedTypeNode> args = new ArrayList<>();
                for (Java.TypeArgument typeArg : refType.typeArguments) {
                    args.add(buildTypeArgument(typeArg));
                }
                ret.setTypeArgs(args);
            }
        }
        return ret;
    }

    public List<ParameterizedTypeNode> buildTypes(Java.Type[] types) {
        if (types == null || types.length == 0)
            return null;

        List<ParameterizedTypeNode> ret = new ArrayList<>();
        for (Java.Type type : types) {
            ret.add(buildType(type));
        }
        return ret;
    }

    public NamedTypeNode buildTypeArgument(Java.TypeArgument arg) {
        if (arg instanceof Java.ReferenceType) {
            return buildType((Java.ReferenceType) arg);
        }
        return new TypeNameNode();
    }

    public ClassDefinition buildInterfaceDeclaration(Java.PackageMemberInterfaceDeclaration type) {
        SourceLocation loc = buildLocation(type);

        ClassDefinition def = new ClassDefinition();
        def.setLocation(loc);
        def.setClassKind(XLangClassKind.INTERFACE);
        def.setName(Identifier.valueOf(loc, type.getName()));
        return def;
    }

    public List<FieldDeclaration> buildFieldDeclarations(Collection<Java.MemberTypeDeclaration> members) {
        return members.stream().map(this::buildFieldDeclaration).collect(Collectors.toList());
    }

    public List<FunctionDeclaration> buildFunctionDeclarations(List<Java.MethodDeclarator> members) {
        return members.stream().map(this::buildFunctionDeclaration).collect(Collectors.toList());
    }

    public FieldDeclaration buildFieldDeclaration(Java.MemberTypeDeclaration member) {
        SourceLocation loc = buildLocation(member);
        FieldDeclaration ret = new FieldDeclaration();
        ret.setLocation(loc);
        ret.setName(Identifier.valueOf(loc, member.getName()));
        return ret;
    }

    public FunctionDeclaration buildFunctionDeclaration(Java.MethodDeclarator method) {
        SourceLocation loc = buildLocation(method);

        FunctionDeclaration ret = new FunctionDeclaration();
        ret.setLocation(loc);
        ret.setName(Identifier.valueOf(loc, method.name));
        if (method.formalParameters != null) {
            List<ParameterDeclaration> params = new ArrayList<>();
            Java.FunctionDeclarator.FormalParameter[] parameters = method.formalParameters.parameters;
            for (int i = 0; i < parameters.length; ++i) {
                params.add(buildParamDeclaration(parameters[i]));
            }

            ret.setParams(params);
        }
        ret.setReturnType(buildType(method.type));
        return ret;
    }

    public ParameterDeclaration buildParamDeclaration(Java.FunctionDeclarator.FormalParameter param) {
        SourceLocation loc = buildLocation(param);
        ParameterDeclaration ret = new ParameterDeclaration();
        ret.setLocation(loc);
        ret.setName(Identifier.valueOf(null, param.name));
        ret.setType(buildType(param.type));
        return ret;
    }

    public EnumDeclaration buildEnumDeclaration(Java.PackageMemberEnumDeclaration type) {
        SourceLocation loc = buildLocation(type);

        EnumDeclaration decl = new EnumDeclaration();
        decl.setName(XLangASTBuilder.identifier(loc, type.getName()));

        List<EnumMember> members = new ArrayList<>();
        for (Java.EnumConstant constant : type.getConstants()) {
            EnumMember member = buildEnumMember(constant);
            members.add(member);
        }
        decl.setMembers(members);
        return decl;
    }

    private EnumMember buildEnumMember(Java.EnumConstant constant) {
        SourceLocation loc = buildLocation(constant);

        EnumMember member = new EnumMember();
        member.setLocation(loc);
        member.setName(Identifier.valueOf(loc, constant.name));
        return member;
    }
}