/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.xmeta.xjava.JaninoHelper;
import org.codehaus.janino.Java;
import org.codehaus.janino.Unparser;

import java.io.IOException;
import java.io.StringWriter;

public class FormatHelper {
    public static String formatJava(SourceLocation loc, String source) {
        Java.AbstractCompilationUnit cu = JaninoHelper.parseJavaSource(loc, source);
        StringWriter out = new StringWriter();
        Unparser.unparse(cu, out);
        return out.toString();
    }

    public static String formatXml(SourceLocation loc, String xml) {
        XNode node = XNodeParser.instance().keepComment(true).parseFromText(loc, xml);
        StringBuilder sb = new StringBuilder();
        try {
            node.saveToWriter(sb);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return sb.toString();
    }
}