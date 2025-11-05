/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.SimpleTextReader;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.web.WebConstants;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;

import static io.nop.web.WebErrors.ARG_LOC;
import static io.nop.web.WebErrors.ERR_WEB_DYNAMIC_FILE_MISSING_END_MOCK;
import static io.nop.web.WebErrors.ERR_WEB_JS_COMMENT_NOT_END_PROPERLY;

public class WebDynamicFileProcessor {
    public String process(SourceLocation loc, String text) {
        MutableString ret = new MutableString();

        SimpleTextReader tokenizer = new SimpleTextReader(loc, text);
        do {
            int startPos = tokenizer.pos();
            tokenizer.skipBlank();

            if (tokenizer.startsWith(WebConstants.PREFIX_INLINE_BEGIN_MOCK)) {
                int endPos = tokenizer.find(WebConstants.PREFIX_INLINE_END_MOCK);
                if (endPos < 0)
                    throw new NopException(ERR_WEB_DYNAMIC_FILE_MISSING_END_MOCK).source(tokenizer);
                tokenizer.moveTo(endPos);
                tokenizer.skipLine();
            } else if (tokenizer.startsWith(WebConstants.PREFIX_MULTILINE_BEGIN_MOCK)) {
                int endPos = tokenizer.find(WebConstants.PREFIX_MULTILINE_END_MOCK);
                if (endPos < 0)
                    throw new NopException(ERR_WEB_DYNAMIC_FILE_MISSING_END_MOCK).source(tokenizer);
                tokenizer.moveTo(endPos);
                tokenizer.skipLine();
            } else if (tokenizer.startsWith("//")) {
                tokenizer.skipLine();
                ret.append(tokenizer.substring(startPos, tokenizer.pos()));
                ret.append('\n');
                continue;
            }
            if (tokenizer.startsWith("/*")) {
                tokenizer.next(2);

                SourceLocation genLoc = tokenizer.location();
                int end = tokenizer.find("*/");
                if (end < 0)
                    throw new NopException(ERR_WEB_JS_COMMENT_NOT_END_PROPERLY)
                            .param(ARG_LOC, genLoc);

                tokenizer.skipChars("*").skipBlank();

                // 动态生成文件内容
                if (tokenizer.tryMatch(WebConstants.PREFIX_GENERATE)) {
                    int pos = tokenizer.pos();
                    String genSource = tokenizer.substring(pos, end);
                    String code = genCode(loc, genSource);
                    ret.append(code).append('\n');
                } else {
                    ret.append(tokenizer.substring(startPos, end + 2));
                }
                tokenizer.moveTo(end + 2);
            } else {
                ret.append(tokenizer.readLine());
                ret.append('\n');
            }
        } while (!tokenizer.isEnd());

        return ret.trim().toString();
    }

    private String genCode(SourceLocation loc, String genSource) {
        if (StringHelper.isBlank(genSource))
            return "";
        XNode node = XNodeParser.instance().parseFromText(loc, genSource);
        XLangCompileTool cp = XLang.newCompileTool();
        IEvalScope scope = XLang.newEvalScope();
        ExprEvalAction action = cp.compileTag(node, XLangOutputMode.text);
        if (action == null)
            return "";
        return action.generateText(scope);
    }
}
