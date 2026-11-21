/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.parse.antlr.XLangLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.util.Set;

/**
 * REPL/watch 编译面沙箱校验器（plan 2478，item 25 审计 SEC-01/02/03/04/06 remediation；
 * D6 §3.3 修订；plan 2586 token/标识符层判定）。取代 ReplService 的纯正则词法近似校验：
 * <p>
 * ① 词法 token 层快检——Promise/async 禁用、{@code __repl*} 保留标识符、禁用全局函数标识符
 * （{@code RegExp/inject/loadDslModel/loadDeltaJson}，D6 §3.3 零 import 能力面收口）。
 * plan 2586：判定全部在 token 层完成（Async/Await 关键字 token + Identifier token 文本对照），
 * 不再对全文跑正则——字符串字面量/注释中出现这些词不再误拒；
 * ② 正则字面量 token 级精确判别——RegularExpressionLiteral 在 AST 层是普通字符串
 * Literal（XLangParseHelper.literalValue 丢弃 flags），只能按 token 类型识别；
 * ③ parse-only AST 遍历——{@code XLangCompileTool.parseFullExpr} 不触发编译期求值
 * （XLangASTBuildVisitor.visitImportAsDeclaration 仅 set source/local），枚举
 * {@link ImportAsDeclaration} 的 QualifiedName（类 FQN）与 Literal（xlib 路径）两态
 * 统一对照白名单，默认拒绝。此层闭合审计三探针：ASI 无分号 import、同行多 import、
 * 字面量 xlib import。
 * <p>
 * 语法错误以 NopException 原样抛出（compile 路径映射诊断、execute 路径映射错误响应）；
 * 校验违规抛 {@link ReplAccessException}(400)。残余面（字符串方法正则回溯 / 内存放大）
 * 见 D6 §3.3 残余清单与 plan 2478 Deferred。
 */
public class ReplSourceValidator {

    static final Set<String> BANNED_IDENTIFIERS =
            Set.of("Promise", "RegExp", "inject", "loadDslModel", "loadDeltaJson");

    /**
     * 编译面沙箱校验。调用时序约束：必须先于任何 compileFullExpr 调用（xlib import
     * 的编译期宏执行发生在 compile 阶段，校验晚于 compile 即失去拦截意义）。
     *
     * @param whitelist import 白名单（类 FQN 或 xlib 路径）；空集 = 拒绝一切 import
     */
    public void validate(String source, SourceLocation loc, Set<String> whitelist) {
        String src = source == null ? "" : source;

        scanSourceTokens(src);

        Program program = XLang.newCompileTool().parseFullExpr(loc, src);
        for (XLangASTNode node : program.getBody()) {
            if (!(node instanceof ImportAsDeclaration))
                continue;
            ImportAsDeclaration imp = (ImportAsDeclaration) node;
            // ESM 风格 ImportDeclaration 经核实不加载任何资源（LexicalScopeAnalysis 未覆写），
            // 非注入面，不在校验范围
            String target = imp.isImportClass() ? imp.getImportClassName()
                    : imp.isImportLib() ? imp.getImportLibPath() : null;
            if (target == null || !whitelist.contains(target))
                throw new ReplAccessException(400, "import not allowed by REPL whitelist: " + target);
        }
    }

    /**
     * 单趟 token 层扫描：async/await 关键字 token、禁用标识符（含 __repl* 保留前缀）与
     * 正则字面量（IsRegexPossible 语义谓词在朴素 nextToken 迭代下正确消歧除法/正则）。
     * hidden channel（空白/注释）跳过——字符串/注释中的禁用词不再误拒（plan 2586）；
     * lexer 错误 token 交由后续 AST 解析显式报错，不在此静默放行。
     */
    private void scanSourceTokens(String source) {
        XLangLexer lexer = new XLangLexer(CharStreams.fromString(source));
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL)
                continue;
            if (token.getType() == XLangLexer.Async || token.getType() == XLangLexer.Await)
                throw new ReplAccessException(400,
                        "Promise/async constructs are disabled in REPL (cancellation unsafety)");
            if (token.getType() == XLangLexer.RegularExpressionLiteral)
                throw new ReplAccessException(400,
                        "regular expression literals are disabled in REPL (cancellation unsafety)");
            if (token.getType() == XLangLexer.Identifier) {
                String text = token.getText();
                if (text.startsWith("__repl"))
                    throw new ReplAccessException(400, "reserved identifier __repl* is not allowed");
                if (BANNED_IDENTIFIERS.contains(text))
                    throw new ReplAccessException(400, "construct not allowed in REPL: " + text);
            }
        }
    }
}
