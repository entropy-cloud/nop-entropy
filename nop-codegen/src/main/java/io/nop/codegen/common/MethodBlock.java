/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.common;

import io.nop.api.core.util.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public class MethodBlock {
    private final SourceLocation loc;
    private final String methodName;

    private List<MethodArgBlock> args = new ArrayList<>();

    private String returnType;

    private boolean isStatic;

    private CodeVisibility visibility;

    private final List<CodeBlock> body = new ArrayList<>();

    public MethodBlock(SourceLocation loc, String methodName) {
        this.loc = loc;
        this.methodName = methodName;
    }

    public MethodBlock addArg(String name, String type, String defaultValue) {
        args.add(new MethodArgBlock(name, type, defaultValue));
        return this;
    }

    public MethodBlock addArg(String name, String type) {
        return addArg(name, type, null);
    }

    public MethodBlock addArg(String name) {
        return addArg(name, null, null);
    }

    public MethodBlock returnType(String returnType) {
        this.returnType = returnType;
        return this;
    }

    public SourceLocation getLocation() {
        return loc;
    }

    public MethodBlock methodStatic(boolean isStatic) {
        this.isStatic = isStatic;
        return this;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public CodeVisibility getVisibility() {
        return visibility;
    }

    public MethodBlock visibility(CodeVisibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<MethodArgBlock> getArgs() {
        return args;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<CodeBlock> getBody() {
        return body;
    }

    public CodeBlock addCodeBlock(SourceLocation loc) {
        CodeBlock block = new CodeBlock(loc);
        this.body.add(block);
        return block;
    }

    public CodeBlock addCodeBlock(SourceLocation loc, String text) {
        return addCodeBlock(loc).append(text);
    }
}