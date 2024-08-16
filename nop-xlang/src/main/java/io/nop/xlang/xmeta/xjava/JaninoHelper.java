/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.xjava;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.Java;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.Unparser;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static io.nop.xlang.XLangErrors.ARG_DETAIL;
import static io.nop.xlang.XLangErrors.ERR_JAVAC_PARSE_FAIL;

public class JaninoHelper {
    public static Java.CompilationUnit parseFromResource(IResource resource) {
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        String source = ResourceHelper.readText(resource);
        return parseJavaSource(loc, source);
    }

    public static Java.CompilationUnit parseJavaSource(SourceLocation loc, String source) {
        if (loc == null)
            loc = SourceLocation.fromClass(JaninoHelper.class);

        String fileName = loc.getPath();

        try {
            Java.AbstractCompilationUnit cu = new Parser(new Scanner(fileName, new StringReader(source)))
                    .parseAbstractCompilationUnit();
            return (Java.CompilationUnit) cu;
        } catch (CompileException e) {
            SourceLocation errorLoc = buildLoc(loc, e);
            String detail = getErrorDetail(e);
            throw new NopException(ERR_JAVAC_PARSE_FAIL, e).loc(errorLoc).param(ARG_DETAIL, detail);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public static String unparse(Java.CompilationUnit unit) {
        StringWriter out = new StringWriter();
        Unparser.unparse(unit, out);
        return out.toString();
    }

    static String getErrorDetail(CompileException e) {
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

    static SourceLocation buildLoc(SourceLocation loc, CompileException e) {
        if (e.getLocation() == null)
            return loc;
        return loc.offset(e.getLocation().getLineNumber() - 1, e.getLocation().getColumnNumber());
    }

    public static SourceLocation buildLoc(SourceLocation baseLoc, Location loc) {
        if (loc == null)
            return null;
        return baseLoc.offset(loc.getLineNumber() - 1, loc.getColumnNumber());
    }
}
