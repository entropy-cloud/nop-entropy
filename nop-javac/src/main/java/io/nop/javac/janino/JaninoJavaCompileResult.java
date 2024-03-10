/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.janino;

import io.nop.api.core.util.Guard;
import io.nop.javac.IJavaCompileResult;
import org.codehaus.janino.Java;
import org.codehaus.janino.Unparser;

import java.io.Writer;

public class JaninoJavaCompileResult implements IJavaCompileResult {
    private final Java.CompilationUnit compilationUnit;
    // private XLangASTNode astNode;

    public JaninoJavaCompileResult(Java.CompilationUnit compilationUnit) {
        this.compilationUnit = Guard.notNull(compilationUnit, "compilationUnit is null");
    }

    // @Override
    // public XLangASTNode getASTNode() {
    // if (astNode == null) {
    // astNode = new JaninoASTNodeBuilder().buildProgram(compilationUnit);
    // }
    // return astNode;
    // }

    public Java.CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    @Override
    public void outputFormattedSource(Writer out) {
        new Unparser(out).unparseAbstractCompilationUnit(compilationUnit);
    }
}