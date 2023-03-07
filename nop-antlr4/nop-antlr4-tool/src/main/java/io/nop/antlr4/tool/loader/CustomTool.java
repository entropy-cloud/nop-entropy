/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.antlr4.tool.loader;

import io.nop.antlr4.tool.AntlrToolConstants;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import org.antlr.v4.Tool;
import org.antlr.v4.tool.Grammar;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class CustomTool extends Tool {
    public CustomTool() {
        Grammar.parserOptions.add(AntlrToolConstants.PARSER_OPTION_AST_IMPORTS);

        Grammar.parseRuleOptions.add(AntlrToolConstants.RULE_OPTION_AST_PROP);
        // Grammar.ruleOptions.add(AntlrToolConstants.OPTION_AST_NODE_NAME);
        Grammar.parseRuleOptions.add(AntlrToolConstants.RULE_OPTION_ELEMENT_AST_NODE_NAME);

        this.errMgr = new CustomErrorManager(this);
        this.errMgr.setFormat("antlr");
    }

    @Override
    public File getOutputDirectory(String fileNameWithPath) {
        // 缺省实现比较诡异，并不以outputDirectory为准。而是检查hasOutputDir属性
        if (outputDirectory != null)
            return new File(outputDirectory);
        return super.getOutputDirectory(fileNameWithPath);
    }

    public Writer getDefaultOutputFileWriter(Grammar g, String fileName) throws IOException {
        return super.getOutputFileWriter(g, fileName);
    }

    public Writer getOutputFileWriter(Grammar g, String fileName) {
        return new NormalizedWriter(g, fileName);
    }

    class NormalizedWriter extends StringWriter {
        private final Grammar g;
        private final String fileName;

        public NormalizedWriter(Grammar g, String fileName) {
            this.g = g;
            this.fileName = fileName;
        }

        public void close() throws IOException {
            String str = this.toString();
            // 这里是一个hack处理，去除输出文件中的绝对路径名
            if (str.startsWith("// Generated from ")) {
                int pos = str.indexOf(" by ANTLR");
                Guard.positiveInt(pos, "invalid pos");
                int prevPos = str.lastIndexOf('/', pos);
                if (prevPos < 0)
                    prevPos = str.lastIndexOf('\\', pos);
                if (prevPos > 0) {
                    if (prevPos == 1) {
                        str = "// Nop Generated from " + str.substring("// Generated from ".length());
                    } else {
                        str = "// Nop Generated from " + str.substring(prevPos + 1);
                    }
                }
            }
            int pos = str.indexOf("Lexer extends Lexer {");
            if (pos > 0) {
                str = str.substring(0, pos) + "Lexer extends io.nop.antlr4.common.AbstractAntlrLexer {"
                        + str.substring(pos + "Lexer extends Lexer {".length());
            }

            String[] pkgNames = new String[]{
                    "import org.antlr.v4.runtime.Lexer;",
                    "import org.antlr.v4.runtime.Token;",
                    "import org.antlr.v4.runtime.TokenStream;",
                    "import org.antlr.v4.runtime.misc.*;",
                    "import java.util.Iterator;",
                    "import java.util.ArrayList;"
            };

            for (String pkgName : pkgNames) {
                str = StringHelper.replace(str, pkgName, pkgName + " //NOPMD - suppressed UnusedImports - Auto Gen Code");
            }

            int pos2 = str.indexOf("public class ");
            if(pos2 > 0){
                str = str.substring(0,pos2) + "// tell cpd to start ignoring code - CPD-OFF\n" + str.substring(pos2)
                        +"\n// resume CPD analysis - CPD-ON";
            }

            Writer out = getDefaultOutputFileWriter(g, fileName);
            try {
                out.write(str);
            } finally {
                out.close();
            }
        }
    }
}
