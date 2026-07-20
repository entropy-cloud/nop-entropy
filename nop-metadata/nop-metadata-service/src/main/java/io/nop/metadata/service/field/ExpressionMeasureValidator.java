/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.field;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.query.MetaAggregationExecutor;
import io.nop.metadata.service.NopMetadataErrors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * expression 型 Measure 文本校验器（§4.4.2 D12.3 安全模型落地，plan 2026-07-18-1400-1）。
 *
 * <p>提供两层校验：
 * <ul>
 *   <li><b>dialect-independent 静态校验</b>（{@link #validateStatic}）：save-time + query-time loader 复用——
 *       关键字/函数黑名单（word-boundary-aware 匹配）+ 标识符白名单（裸名或 {@code l.}/{@code r.} 限定）
 *       + 字面量参数绑定（数值 / 单引号字符串字面量收集为 {@code ?}）+ parse 结构（括号匹配、禁语句终止符 / 注释）。</li>
 *   <li><b>dialect-specific 函数支持检查</b>（{@link #checkDialectSupported}）：SQL 构造阶段调用，
 *       按方言拒绝当前方言不支持的函数（如 MySQL 不支持 {@code DATE_TRUNC}）。</li>
 * </ul>
 *
 * <p><b>分词机制裁定（word-boundary-aware + safe-side）</b>：关键字检测使用 word-boundary-aware 匹配
 * （如 {@code \bDROP\b}），稳健 against 标识符嵌入（{@code DROP_DATE} 列名中 {@code DROP} 不独立成词——
 * {@code _} 是 word char，{@code \bDROP\b} 不匹配 {@code DROP_DATE} 中的 DROP）。
 * <b>已知限制（safe-side 偏差，可接受——拒绝比放行安全）</b>：字符串字面量内的关键字（{@code 'DROP'}）
 * 经分词阶段已被收集为 {@code ?}，从文本中移除后再做关键字扫描，故字符串内关键字不会触发误拒；
 * 但裸字符串拼接的 SQL（用户绕过 validator）不在本组件防御面内。
 *
 * <p><b>不使用</b>裸 {@code contains} 子串匹配（可被注释 {@code -- DROP}/字符串绕过）。
 *
 * <p><b>失败一律显式抛 ErrorCode</b>（不静默 fallback、不静默 sanitize、不静默截断）。
 *
 * <p>无状态，可在多 BizModel/Executor 间共享实例。
 */
public final class ExpressionMeasureValidator {

    /** expression 列容量上限（VARCHAR(1000)，与 {@code nop-metadata.orm.xml} 列定义一致）。 */
    public static final int EXPRESSION_MAX_LENGTH = 1000;

    /** SQL 标识符白名单正则（与 {@code FilterToSqlTranslator.IDENTIFIER_PATTERN} 一致）。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /**
     * 关键字黑名单（dialect-independent 通用集，word-boundary 匹配）。
     *
     * <p>含 DML / DDL / DCL / 事务 / 过程调用 / 系统副作用关键字。
     */
    private static final Set<String> KEYWORD_BLACKLIST = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            // DDL
            "DROP", "CREATE", "ALTER", "TRUNCATE", "RENAME",
            // DML (除 SELECT 系列外)
            "INSERT", "UPDATE", "DELETE", "MERGE", "REPLACE",
            // DCL / TCL
            "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "SAVEPOINT", "SET TRANSACTION",
            // 过程调用
            "CALL", "EXEC", "EXECUTE",
            // 其他系统副作用
            "SHUTDOWN", "LOCK", "UNLOCK"
    )));

    /**
     * 函数黑名单（dialect-independent 通用集，word-boundary 匹配，跟在左括号前）。
     *
     * <p>含 MySQL / PostgreSQL / 通用已知的副作用函数。dialect-specific 函数支持（如 MySQL 不支持 {@code DATE_TRUNC}）
     * 由 {@link #checkDialectSupported} 在 SQL 构造阶段按方言校验。
     */
    private static final Set<String> FUNCTION_BLACKLIST = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            // MySQL 副作用
            "SLEEP", "BENCHMARK", "LOAD_FILE", "GET_LOCK", "RELEASE_LOCK",
            "INTO OUTFILE", "INTO DUMPFILE",
            // PostgreSQL 副作用
            "PG_SLEEP", "PG_TERMINATE_BACKEND", "COPY",
            // 通用文件 / 命令
            "xp_cmdshell"
    )));

    /** JOIN 上下文标识符前缀（小写别名 l./r.）。 */
    private static final String JOIN_LEFT_PREFIX = "l.";
    private static final String JOIN_RIGHT_PREFIX = "r.";

    /** capacity 限制（>= EXPRESSION_MAX_LENGTH 即失败，与 VARCHAR(1000) 一致）。 */
    public static void checkCapacity(String expression, String metaTableId, String measureName) {
        if (expression == null) {
            return;
        }
        int len = expression.length();
        if (len > EXPRESSION_MAX_LENGTH) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_TOO_LONG)
                    .param("metaTableId", metaTableId)
                    .param("measureName", measureName)
                    .param("length", len)
                    .param("limit", EXPRESSION_MAX_LENGTH);
        }
    }

    /**
     * dialect-independent 静态校验（save-time + query-time loader 复用）。
     *
     * <p>分词：扫描 expression 文本，逐 token 分类（标识符 / 数值字面量 / 单引号字符串字面量 / 算子与标点）。
     * 字面量统一替换为 {@code ?} 并按顺序收集到 {@code params}；标识符按当前上下文（裸列 / JOIN 限定）校验白名单；
     * 关键字 / 函数黑名单经 word-boundary 匹配。
     *
     * <p><b>save-time 宽松裁定（R2 修复）</b>：save-time 不知道 measure 将用于单表还是 JOIN，接受裸列名 **和**
     * {@code l.}/{@code r.} 前缀列名（前缀部分通过白名单正则即可，不校验列存在性 / 端点归属——这些延迟到
     * query-time loader）。save-time 聚焦：关键字黑名单 + 容量 + parse 结构合法性。
     *
     * @param expression expression 文本（非空）
     * @param options    校验选项（标识符白名单策略：单表 / JOIN / save-time 宽松；可选 expectedColumns 严格校验列存在性）
     * @param metaTableId  错误上下文
     * @param measureName 错误上下文
     * @return 校验结果（sqlFragment + params + identifiers + functions），永不返回 null
     * @throws NopException unparseable / unsafe 时抛对应 ErrorCode
     */
    public static ValidatedExpression validateStatic(String expression, ValidationOptions options,
                                                     String metaTableId, String measureName) {
        if (expression == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                    .param("metaTableId", metaTableId).param("measureName", measureName)
                    .param("expression", String.valueOf(expression)).param("error", "expression is null");
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                    .param("metaTableId", metaTableId).param("measureName", measureName)
                    .param("expression", expression).param("error", "expression is empty after trim");
        }
        // 容量校验（save-time 入口）
        checkCapacity(expression, metaTableId, measureName);

        TokenizeResult result = tokenize(expression, metaTableId, measureName);
        // 重新扫描 token 列表做关键字 / 函数黑名单检测（已剔除字符串字面量，safe-side）
        scanBlacklist(result.tokens, expression, metaTableId, measureName);

        // 标识符白名单校验 + 可选列存在性校验
        Set<String> identifiers = new LinkedHashSet<>();
        Set<String> functions = new LinkedHashSet<>();
        for (Token tok : result.tokens) {
            if (tok.type == TokenType.IDENTIFIER) {
                // 单 token 标识符：白名单校验（裸名格式），可选列存在性校验
                String ident = tok.text;
                if (!IDENTIFIER_PATTERN.matcher(ident).matches()) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "identifier fails whitelist ^[A-Za-z_][A-Za-z0-9_]*$: " + ident);
                }
                identifiers.add(ident);
            } else if (tok.type == TokenType.QUALIFIED_IDENTIFIER) {
                // 形如 l.col / r.col：JOIN 限定名
                int dot = tok.text.indexOf('.');
                String prefix = tok.text.substring(0, dot);
                String col = tok.text.substring(dot + 1);
                if (!IDENTIFIER_PATTERN.matcher(prefix).matches()) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "join qualifier prefix fails whitelist: " + prefix);
                }
                if (!IDENTIFIER_PATTERN.matcher(col).matches()) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "qualified identifier column fails whitelist: " + col);
                }
                // JOIN 上下文限定名须为 l./r.（其他前缀如 a.b 不允许）
                if (!JOIN_LEFT_PREFIX.equalsIgnoreCase(prefix + ".")
                        && !JOIN_RIGHT_PREFIX.equalsIgnoreCase(prefix + ".")) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "join qualifier must be l. or r. but got: " + prefix + ".");
                }
                // JOIN 上下文要求：若 options.requireJoinQualifier=true，qualifier 必须存在；此处已存在（QUALIFIED token）
                // 单表上下文要求：若 options.allowQualified=false，qualifier 禁止
                if (!options.allowQualified) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "qualified identifier (l./r.) not allowed in single-table context: " + tok.text);
                }
                // JOIN 上下文：按 side 解析对应端点列集合（左前缀→leftColumns / 右前缀→rightColumns）。
                // 注意：单表上下文不允许 QUALIFIED token（已由上面 allowQualified=false 守卫拒绝），
                // 故进入此处即 JOIN 上下文，使用 leftColumns/rightColumns 而非 expectedColumns。
                if (options.leftColumns != null || options.rightColumns != null) {
                    Set<String> sideCols = "l".equalsIgnoreCase(prefix) ? options.leftColumns : options.rightColumns;
                    if (sideCols == null || !containsIgnoreCase(sideCols, col)) {
                        throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                                .param("metaTableId", metaTableId).param("measureName", measureName)
                                .param("expression", expression)
                                .param("reason", "qualified column not in declared side endpoint field set: " + tok.text);
                    }
                }
                identifiers.add(tok.text);
            } else if (tok.type == TokenType.KEYWORD_LIKE) {
                // 关键字 token（CASE/WHEN/THEN/ELSE/END 等允许的查询构造关键字）——不做白名单
            } else if (tok.type == TokenType.FUNCTION_CALL) {
                functions.add(tok.text);
            }
        }
        // 单表上下文：裸标识符可选列存在性校验
        if (options.expectedColumns != null) {
            for (String ident : identifiers) {
                if (ident.contains(".")) {
                    continue; // 已在上面按 side 校验
                }
                if (!containsIgnoreCase(options.expectedColumns, ident)) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "column not in resolved field set: " + ident);
                }
            }
        }
        // JOIN 上下文要求：所有裸标识符必须限定（除非 allowBareInJoin=true for entity 侧 fallback）
        if (options.requireJoinQualifier) {
            for (String ident : identifiers) {
                if (!ident.contains(".")) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "unqualified column in JOIN context (must be l./r. qualified): " + ident);
                }
            }
        }

        return new ValidatedExpression(expression, result.sqlFragment, result.params, identifiers, functions);
    }

    /** 大小写不敏感集合包含判断（H2 列名常大写 vs 配置可能小写）。 */
    private static boolean containsIgnoreCase(Set<String> cols, String name) {
        if (cols == null || name == null) {
            return false;
        }
        for (String c : cols) {
            if (c != null && c.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 分词：扫描 expression 文本，逐 token 分类。
     *
     * <p>同时构建 SQL 片段：字面量替换为 {@code ?}，其他 token 原样输出（用空格分隔以保持 token 边界，
     * 避免 {@code aIN(b)} 等无空格输入被误识别）。
     *
     * @throws NopException parse 失败（未闭合括号 / 字符串字面量未闭合 / 语句终止符 / 注释标记）
     */
    private static TokenizeResult tokenize(String expression, String metaTableId, String measureName) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        int parenDepth = 0;
        int n = expression.length();
        int i = 0;
        boolean firstOutput = true;
        while (i < n) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // 语句终止符 / 注释标记 → 直接拒绝（safe-side）
            if (c == ';') {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                        .param("metaTableId", metaTableId).param("measureName", measureName)
                        .param("expression", expression)
                        .param("error", "statement terminator ';' is not allowed");
            }
            if (c == '-' && i + 1 < n && expression.charAt(i + 1) == '-') {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                        .param("metaTableId", metaTableId).param("measureName", measureName)
                        .param("expression", expression)
                        .param("error", "line comment '--' is not allowed");
            }
            if (c == '/' && i + 1 < n && expression.charAt(i + 1) == '*') {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                        .param("metaTableId", metaTableId).param("measureName", measureName)
                        .param("expression", expression)
                        .param("error", "block comment '/*' is not allowed");
            }
            if (c == '(') {
                parenDepth++;
                tokens.add(new Token(TokenType.LPAREN, "("));
                sql.append(firstOutput ? "" : " ").append("(");
                firstOutput = false;
                i++;
                continue;
            }
            if (c == ')') {
                parenDepth--;
                if (parenDepth < 0) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("error", "unbalanced parenthesis: extra ')'");
                }
                tokens.add(new Token(TokenType.RPAREN, ")"));
                sql.append(" )");
                firstOutput = false;
                i++;
                continue;
            }
            // 单引号字符串字面量 '...' （含 '' 转义）
            if (c == '\'') {
                int start = i;
                i++; // 跳过开头 '
                StringBuilder lit = new StringBuilder();
                boolean closed = false;
                while (i < n) {
                    char ch = expression.charAt(i);
                    if (ch == '\'') {
                        if (i + 1 < n && expression.charAt(i + 1) == '\'') {
                            // 转义 ''
                            lit.append('\'');
                            i += 2;
                            continue;
                        }
                        // 结束
                        i++;
                        closed = true;
                        break;
                    }
                    lit.append(ch);
                    i++;
                }
                if (!closed) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("error", "unclosed string literal starting at offset " + start);
                }
                tokens.add(new Token(TokenType.STRING_LITERAL, lit.toString()));
                sql.append(firstOutput ? "" : " ").append("?");
                params.add(lit.toString());
                firstOutput = false;
                continue;
            }
            // 数值字面量（含小数）
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(expression.charAt(i + 1)))) {
                int start = i;
                boolean sawDot = (c == '.');
                i++;
                while (i < n) {
                    char ch = expression.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && !sawDot) {
                        sawDot = true;
                        i++;
                    } else {
                        break;
                    }
                }
                String num = expression.substring(start, i);
                tokens.add(new Token(TokenType.NUMERIC_LITERAL, num));
                sql.append(firstOutput ? "" : " ").append("?");
                // 数值统一以 BigDecimal 承载（避免 Integer/Long/Double 类型在 PreparedStatement 上的差异）
                params.add(new java.math.BigDecimal(num));
                firstOutput = false;
                continue;
            }
            // 标识符 / 关键字 / 函数名（letter/underscore 开头）
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                i++;
                while (i < n && (Character.isLetterOrDigit(expression.charAt(i)) || expression.charAt(i) == '_')) {
                    i++;
                }
                String word = expression.substring(start, i);
                // 检测是否为限定名（word . word），如 l.col / r.col
                if (i < n && expression.charAt(i) == '.') {
                    int dotPos = i;
                    i++; // 跳过点
                    if (i < n && (Character.isLetter(expression.charAt(i)) || expression.charAt(i) == '_')) {
                        int colStart = i;
                        i++;
                        while (i < n && (Character.isLetterOrDigit(expression.charAt(i)) || expression.charAt(i) == '_')) {
                            i++;
                        }
                        String col = expression.substring(colStart, i);
                        String qualified = word + "." + col;
                        tokens.add(new Token(TokenType.QUALIFIED_IDENTIFIER, qualified));
                        sql.append(firstOutput ? "" : " ").append(qualified);
                        firstOutput = false;
                        continue;
                    }
                    // 点后非标识符——视为非法限定（不静默接受）
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("error", "illegal qualified identifier after '.' at offset " + dotPos);
                }
                // 检测是否为函数调用（word 紧跟左括号）
                String upper = word.toUpperCase(Locale.ROOT);
                int savedI = i;
                // 跳过空白后看是否为 '('
                int j = i;
                while (j < n && Character.isWhitespace(expression.charAt(j))) {
                    j++;
                }
                if (j < n && expression.charAt(j) == '(') {
                    tokens.add(new Token(TokenType.FUNCTION_CALL, upper));
                    sql.append(firstOutput ? "" : " ").append(word);
                    firstOutput = false;
                    // 不消费 '('——下一轮循环会处理 LPAREN
                    continue;
                }
                i = savedI;
                // 关键字（允许的查询构造关键字 CASE/WHEN/THEN/ELSE/END/AND/OR/NOT/IS/NULL/LIKE/IN/BETWEEN/DISTINCT/ASC/DESC）
                if (isAllowedKeyword(upper)) {
                    tokens.add(new Token(TokenType.KEYWORD_LIKE, upper));
                    sql.append(firstOutput ? "" : " ").append(word);
                    firstOutput = false;
                    continue;
                }
                // 普通标识符
                tokens.add(new Token(TokenType.IDENTIFIER, word));
                sql.append(firstOutput ? "" : " ").append(word);
                firstOutput = false;
                continue;
            }
            // 其他算子与标点（+ - * / % = <> <= >= < > , 等）——单字符透传
            tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
            sql.append(firstOutput ? "" : " ").append(c);
            firstOutput = false;
            i++;
        }
        if (parenDepth != 0) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNPARSEABLE)
                    .param("metaTableId", metaTableId).param("measureName", measureName)
                    .param("expression", expression)
                    .param("error", "unbalanced parenthesis: " + parenDepth + " unclosed '('");
        }
        return new TokenizeResult(tokens, sql.toString(), params);
    }

    /** 允许的查询构造关键字（SQL 表达式中合法、非 DML/DDL/副作用）。 */
    private static final Set<String> ALLOWED_KEYWORDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "CASE", "WHEN", "THEN", "ELSE", "END",
            "AND", "OR", "NOT", "IS", "NULL", "LIKE", "IN", "BETWEEN",
            "DISTINCT", "ASC", "DESC",
            "TRUE", "FALSE",
            "CAST", "NULLIF", "COALESCE"
    )));

    private static boolean isAllowedKeyword(String upper) {
        return ALLOWED_KEYWORDS.contains(upper);
    }

    /**
     * 关键字 / 函数黑名单扫描（word-boundary-aware）。
     *
     * <p>对 token 流扫描：纯关键字（非函数）检测是否命中 {@link #KEYWORD_BLACKLIST}；
     * 函数名 token（{@code foo(}）检测是否命中 {@link #FUNCTION_BLACKLIST}。
     * 由于字符串字面量已被分词阶段收集为字面量 token（其文本保留在 params，不出现在 SQL fragment 中），
     * 故字符串内关键字不会触发误拒。
     */
    private static void scanBlacklist(List<Token> tokens, String expression,
                                      String metaTableId, String measureName) {
        for (Token tok : tokens) {
            if (tok.type == TokenType.IDENTIFIER) {
                String upper = tok.text.toUpperCase(Locale.ROOT);
                if (KEYWORD_BLACKLIST.contains(upper)) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "forbidden keyword: " + upper);
                }
            } else if (tok.type == TokenType.FUNCTION_CALL) {
                if (FUNCTION_BLACKLIST.contains(tok.text)) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_UNSAFE)
                            .param("metaTableId", metaTableId).param("measureName", measureName)
                            .param("expression", expression)
                            .param("reason", "forbidden function: " + tok.text);
                }
            }
        }
    }

    /**
     * dialect-specific 函数支持检查（SQL 构造阶段调用，dialect 已知）。
     *
     * <p>按方言拒绝当前方言不支持的函数。如 MySQL 不支持 {@code DATE_TRUNC}（MySQL 用 {@code DATE_FORMAT}）。
     * H2 与 PostgreSQL 通用支持 {@code DATE_TRUNC} 系列与 {@code STDDEV_SAMP} 等。
     *
     * @param validated   validator 静态校验产出物（含 functions 集合）
     * @param dialect     数据库方言（{@code H2} / {@code MySQL} / {@code PostgreSQL}，大小写敏感匹配 SUPPORTED_DIALECTS）
     * @param metaTableId 错误上下文
     * @param measureName 错误上下文
     * @throws NopException dialect 不支持某函数时抛 {@link MetaAggregationExecutor#ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED}
     */
    public static void checkDialectSupported(ValidatedExpression validated, String dialect,
                                             String metaTableId, String measureName) {
        if (validated == null || validated.functions == null || validated.functions.isEmpty()) {
            return;
        }
        for (String fn : validated.functions) {
            if (!isFunctionSupported(fn, dialect)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED)
                        .param("metaTableId", metaTableId).param("measureName", measureName)
                        .param("expression", validated.expression)
                        .param("databaseProductName", String.valueOf(dialect))
                        .param("unsupportedToken", fn);
            }
        }
    }

    /** MySQL 不支持的函数集合（其余视为 H2/PostgreSQL/MySQL 通用支持）。 */
    private static final Set<String> MYSQL_UNSUPPORTED = Collections.singleton("DATE_TRUNC");

    private static boolean isFunctionSupported(String function, String dialect) {
        if (function == null || function.isEmpty()) {
            return true;
        }
        if ("MySQL".equals(dialect)) {
            return !MYSQL_UNSUPPORTED.contains(function);
        }
        // H2 / PostgreSQL（与 SUPPORTED_DIALECTS 一致）—— 首版认为通用支持
        return true;
    }

    // ============================================================
    // 内部数据结构
    // ============================================================

    private enum TokenType {
        IDENTIFIER, QUALIFIED_IDENTIFIER, NUMERIC_LITERAL, STRING_LITERAL,
        FUNCTION_CALL, KEYWORD_LIKE, OPERATOR, LPAREN, RPAREN
    }

    private static final class Token {
        final TokenType type;
        final String text;

        Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static final class TokenizeResult {
        final List<Token> tokens;
        final String sqlFragment;
        final List<Object> params;

        TokenizeResult(List<Token> tokens, String sqlFragment, List<Object> params) {
            this.tokens = tokens;
            this.sqlFragment = sqlFragment;
            this.params = params;
        }
    }

    // ============================================================
    // 公开类型
    // ============================================================

    /** 校验选项（标识符白名单策略）。 */
    public static final class ValidationOptions {
        /** 允许 l./r. 限定名（save-time 宽松 + JOIN 上下文 = true；单表上下文 = false）。 */
        public final boolean allowQualified;
        /** JOIN 上下文要求所有列引用限定为 l./r.（save-time 宽松 = false；JOIN = true）。 */
        public final boolean requireJoinQualifier;
        /** 单表上下文列集合（裸名严格校验；null = 不校验列存在性）。 */
        public final Set<String> expectedColumns;
        /** JOIN 左端点列集合（l. 前缀列名校验；null = 不校验）。 */
        public final Set<String> leftColumns;
        /** JOIN 右端点列集合（r. 前缀列名校验；null = 不校验）。 */
        public final Set<String> rightColumns;

        private ValidationOptions(boolean allowQualified, boolean requireJoinQualifier,
                                  Set<String> expectedColumns, Set<String> leftColumns, Set<String> rightColumns) {
            this.allowQualified = allowQualified;
            this.requireJoinQualifier = requireJoinQualifier;
            this.expectedColumns = expectedColumns;
            this.leftColumns = leftColumns;
            this.rightColumns = rightColumns;
        }

        /** save-time 宽松：接受裸列名 **和** l./r. 前缀；不校验列存在性 / 端点归属。 */
        public static ValidationOptions saveTimeLoose() {
            return new ValidationOptions(true, false, null, null, null);
        }

        /** 单表上下文严格：仅裸列名；可选校验列存在性（expectedColumns 非 null 时）。 */
        public static ValidationOptions singleTableStrict(Set<String> expectedColumns) {
            return new ValidationOptions(false, false, expectedColumns, null, null);
        }

        /** JOIN 上下文严格：要求 l./r. 限定名；可选校验列归属（leftColumns/rightColumns 非 null 时）。 */
        public static ValidationOptions joinStrict(Set<String> leftColumns, Set<String> rightColumns) {
            return new ValidationOptions(true, true, null, leftColumns, rightColumns);
        }
    }

    /** validator 产出物：原始文本 + SQL 片段（含 {@code ?} 占位符）+ 字面量参数列表 + 标识符集合 + 函数集合。 */
    public static final class ValidatedExpression {
        public final String expression;
        public final String sqlFragment;
        public final List<Object> params;
        public final Set<String> identifiers;
        public final Set<String> functions;

        ValidatedExpression(String expression, String sqlFragment, List<Object> params,
                            Set<String> identifiers, Set<String> functions) {
            this.expression = expression;
            this.sqlFragment = sqlFragment;
            this.params = params;
            this.identifiers = Collections.unmodifiableSet(identifiers);
            this.functions = Collections.unmodifiableSet(functions);
        }
    }
}
