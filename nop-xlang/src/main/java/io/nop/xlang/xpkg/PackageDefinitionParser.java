/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpkg;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.impl.FunctionModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.ExportDeclaration;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.IXLangASTNode;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.compile.BuildExecutableProcessor;
import io.nop.xlang.exec.ExecutableFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackageDefinitionParser extends AbstractResourceParser<PackageDefinition> {

    @Override
    protected PackageDefinition doParseResource(IResource resource) {
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());

        String text = ResourceHelper.readText(resource, StringHelper.ENCODING_UTF8);
        XLangCompileTool tool = XLang.newCompileTool();

        Program prog = tool.parseFullExpr(loc, text);
        return new BuildPackageDefinitionProcessor().buildPackageDefinition(prog, tool.getScope());
    }

    static class BuildPackageDefinitionProcessor extends BuildExecutableProcessor {
        PackageDefinition buildPackageDefinition(Program prog, IXLangCompileScope scope) {
            PackageDefinition def = new PackageDefinition();
            def.setLocation(prog.getLocation());
            List<XLangASTNode> list = prog.getBody();
            if (list == null || list.isEmpty())
                return def;

            // 先编译函数定义
            for (XLangASTNode child : list) {
                child = getNormalOrExported(child);

                if (child.getASTKind() == XLangASTKind.FunctionDeclaration) {
                    FunctionDeclaration decl = (FunctionDeclaration) child;
                    ExecutableFunction fn = compileFuncDecl(decl, scope);
                    executableFuncs.put(decl.getName().getToken(), new FuncData(fn));
                }
            }

            Map<String, IConstantDefinition> consts = new HashMap<>();
            Map<String, IFunctionModel> funcs = new HashMap<>();

            // 只允许函数定义和常量定义
            for (IXLangASTNode child : list) {
                boolean exported = false;
                if (child.getASTKind() == XLangASTKind.ExportDeclaration) {
                    exported = true;
                    child = ((ExportDeclaration) child).getDeclaration();
                }

                XLangASTKind kind = child.getASTKind();

                switch (kind) {
                    case ImportAsDeclaration:
                    case ImportDeclaration:
                        continue;
                    case FunctionDeclaration: {
                        FunctionDeclaration decl = (FunctionDeclaration) child;
                        IExecutableExpression body = buildFunctionBody(decl, scope);
                        FuncData fn = executableFuncs.get(decl.getName().getToken());
                        fn.setBody(body);

                        if (exported) {
                            IFunctionModel funcModel = buildFunctionModel(decl, fn.getFunction());
                            funcs.put(funcModel.getName(), funcModel);
                        }
                        break;
                    }
                    case AssignmentExpression: {
                        AssignmentExpression assign = (AssignmentExpression) child;
                        assign.getLeft();
                    }
                }
            }

            def.setFunctions(funcs);
            def.setConstants(consts);

            return def;
        }

        IFunctionModel buildFunctionModel(FunctionDeclaration decl, ExecutableFunction fn) {
            FunctionModel model = new FunctionModel();
            model.setInvoker(fn);
            model.setMacro(decl.hasDecorator(XLangConstants.DECORATOR_MACRO));
            model.setDeprecated(decl.hasDecorator(XLangConstants.DECORATOR_DEPRECATED));
            model.setDeterministic(decl.hasDecorator(XLangConstants.DECORATOR_DETERMINISTIC));
            return model;
        }
    }
}