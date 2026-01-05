/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.javac.janino.JaninoClassLoader;
import io.nop.javac.janino.JaninoJavaParseResult;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Java;
import org.codehaus.janino.Java.AbstractCompilationUnit;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static io.nop.javac.JavaCompilerErrors.ARG_DETAIL;
import static io.nop.javac.JavaCompilerErrors.ERR_JAVAC_PARSE_FAIL;

public class JavaCompileTool implements IJavaCompileTool {
    private static JavaCompileTool _INSTANCE = new JavaCompileTool();

    public static JavaCompileTool instance() {
        return _INSTANCE;
    }

    public String formatJavaSource(SourceLocation loc, String source) {
        IJavaParseResult result = parseJavaSource(loc, source);
        return result.getFormattedSource();
    }

    public IJavaParseResult parseJavaSource(SourceLocation loc, String source) {
        if (loc == null)
            loc = SourceLocation.fromClass(JavaCompileTool.class);
        String fileName = loc.getPath();

        try {
            AbstractCompilationUnit cu = new Parser(new Scanner(fileName, new StringReader(source)))
                    .parseAbstractCompilationUnit();
            return new JaninoJavaParseResult((Java.CompilationUnit) cu);
        } catch (CompileException e) {
            SourceLocation errorLoc = buildLoc(loc, e);
            String detail = getErrorDetail(e);
            throw new NopException(ERR_JAVAC_PARSE_FAIL, e).loc(errorLoc).param(ARG_DETAIL, detail);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    String getErrorDetail(CompileException e) {
        Location loc = e.getLocation();
        if (loc == null)
            return e.getMessage();

        String locStr = loc.toString();
        String msg = e.getMessage();
        if (msg.startsWith(locStr)) {
            return msg.substring(locStr.length() + 2);
        }
        return msg;
    }

    SourceLocation buildLoc(SourceLocation loc, CompileException e) {
        if (e.getLocation() == null)
            return loc;
        return loc.offset(e.getLocation().getLineNumber() - 1, e.getLocation().getColumnNumber());
    }

    @Override
    public IDynamicClassLoader createDynamicClassLoader(ClassLoader parentLoader, JavaLibConfig config) {
        parentLoader = buildParent(parentLoader, config);
        return JaninoClassLoader.create(parentLoader, config.getSourceDirs().toArray(new File[0]),
                config.getCacheDir());
    }

    private ClassLoader buildParent(ClassLoader parentLoader, JavaLibConfig config) {
        if (config.getLibDirs().isEmpty() && config.getClassesDirs().isEmpty())
            return parentLoader;

        DynamicURLClassLoader loader = new DynamicURLClassLoader("dynamic-lib-loader", parentLoader);
        for (File dir : config.getClassesDirs()) {
            loader.addClassesDir(dir);
        }

        for (File dir : config.getLibDirs()) {
            loader.addJarDir(dir);
        }

        return loader;
    }
}