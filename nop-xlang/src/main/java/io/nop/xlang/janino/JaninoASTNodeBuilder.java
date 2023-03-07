/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.janino;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.javac.JavaCompilerErrors;
import io.nop.xlang.ast.ClassDefinition;
import io.nop.xlang.ast.Declaration;
import io.nop.xlang.ast.EnumDeclaration;
import io.nop.xlang.ast.EnumMember;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangClassKind;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Java;

import java.util.ArrayList;
import java.util.List;

public class JaninoASTNodeBuilder {

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

    protected String buildFilePath(String path) {
        return path;
    }

    private SourceLocation buildLocation(Java.Locatable node) {
        Location loc = node.getLocation();
        String path = buildFilePath(loc.getFileName());
        return SourceLocation.fromLine(path, loc.getLineNumber(), loc.getColumnNumber());
    }

    public ImportAsDeclaration buildImportDeclaration(Java.AbstractCompilationUnit.ImportDeclaration decl) {
        if (decl instanceof Java.AbstractCompilationUnit.SingleTypeImportDeclaration) {
            return buildImportAsDeclaration((Java.AbstractCompilationUnit.SingleTypeImportDeclaration) decl);
        } else {
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

    public Declaration buildDeclaration(Java.PackageMemberTypeDeclaration type) {
        if (type instanceof Java.PackageMemberEnumDeclaration) {
            return buildEnumDeclaration((Java.PackageMemberEnumDeclaration) type);
        } else if (type instanceof Java.PackageMemberClassDeclaration) {
            return buildClassDeclaration((Java.PackageMemberClassDeclaration) type);
        } else if (type instanceof Java.PackageMemberInterfaceDeclaration) {
            return buildInterfaceDeclaration((Java.PackageMemberInterfaceDeclaration) type);
        } else {
            throw newTransformError(type, XLangASTKind.ClassDefinition);
        }
    }

    public ClassDefinition buildClassDeclaration(Java.PackageMemberClassDeclaration type) {
        SourceLocation loc = buildLocation(type);

        ClassDefinition def = new ClassDefinition();
        def.setLocation(loc);
        def.setClassKind(XLangClassKind.CLASS);
        return def;
    }

    public ClassDefinition buildInterfaceDeclaration(Java.PackageMemberInterfaceDeclaration type) {
        SourceLocation loc = buildLocation(type);

        ClassDefinition def = new ClassDefinition();
        def.setLocation(loc);
        def.setClassKind(XLangClassKind.INTERFACE);
        return def;
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