/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.tokenizer;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.text.MutableString;
import io.nop.commons.util.StringHelper;

import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonErrors.ARG_CUR;
import static io.nop.commons.CommonErrors.ARG_EOF;
import static io.nop.commons.CommonErrors.ARG_EXPECTED;
import static io.nop.commons.CommonErrors.ARG_POS;
import static io.nop.commons.CommonErrors.ARG_READER_STATE;
import static io.nop.commons.CommonErrors.ARG_START_LOC;
import static io.nop.commons.CommonErrors.ERR_SCAN_BLANK_EXPECTED;
import static io.nop.commons.CommonErrors.ERR_SCAN_COMMENT_UNEXPECTED_EOF;
import static io.nop.commons.CommonErrors.ERR_SCAN_ILLEGAL_ESCAPE_CHAR;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_CHAR;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_DOUBLE_STRING;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_ESCAPE_UNICODE;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_FLOAT_STRING;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_HEX_INT_STRING;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_LONG_STRING;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_NUMBER_STRING;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_PROP_PATH;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_VAR;
import static io.nop.commons.CommonErrors.ERR_SCAN_INVALID_XML_NAME;
import static io.nop.commons.CommonErrors.ERR_SCAN_NEXT_UNTIL_UNEXPECTED_EOF;
import static io.nop.commons.CommonErrors.ERR_SCAN_NOT_ALLOW_TWO_SEPARATOR_IN_XML_NAME;
import static io.nop.commons.CommonErrors.ERR_SCAN_NOT_DIGIT;
import static io.nop.commons.CommonErrors.ERR_SCAN_NOT_END_PROPERLY;
import static io.nop.commons.CommonErrors.ERR_SCAN_NOT_HEX_CHAR;
import static io.nop.commons.CommonErrors.ERR_SCAN_NUMBER_NOT_INT;
import static io.nop.commons.CommonErrors.ERR_SCAN_STRING_NOT_END;
import static io.nop.commons.CommonErrors.ERR_SCAN_TOKEN_END_EXPECTED;
import static io.nop.commons.CommonErrors.ERR_SCAN_UNEXPECTED_CHAR;
import static io.nop.commons.CommonErrors.ERR_SCAN_UNEXPECTED_STR;
import static io.nop.commons.CommonErrors.ERR_SCAN_UNEXPECTED_TOKEN;
import static io.nop.commons.CommonErrors.ERR_TEXT_ILLEGAL_HEX_STRING;
import static io.nop.commons.CommonErrors.ERR_TEXT_NUMBER_STARTS_WITH_ZERO;
import static io.nop.commons.util.StringHelper.isGraphQLNamePart;
import static io.nop.commons.util.StringHelper.isGraphQLNameStart;

/**
 * 支持预读两个字符的词法分析工具类。
 */
public class TextScanner {
    static final SourceLocation DEFAULT_LOCATION = SourceLocation.fromPath("text");

    private final SourceLocation baseLoc;
    private SourceLocation prevLoc;

    protected final ICharReader reader;

    private boolean rangePosition;

    private final MutableString localBuf = new MutableString();

    private int lineState;

    private int prevLine;
    private int prevCol;
    private int prevPos;

    public int line;
    public int col;
    public int pos; // 当前字符对应的字符位置
    public int cur; // 当前字符，-1表示已结束

    public boolean lineSkipped; // 刚刚调用的读取函数是否读到了回车或者换行符
    public boolean blankSkipped;

    public boolean useEvalException;

    public IToken peekToken;
    public int peekTokenLen;

    TextScanner(SourceLocation loc, ICharReader reader) {
        this.baseLoc = loc == null ? DEFAULT_LOCATION : loc;
        this.reader = reader;
        this.useEvalException = false;
        this.line = baseLoc.getLine();
        this.col = baseLoc.getCol();
        this.pos = -1;
        this.prevLoc = baseLoc;
        next();
    }

    public CharSequence getBaseSequence() {
        if (reader instanceof CharSequenceReader)
            return ((CharSequenceReader) reader).getSequence();
        return null;
    }

    /**
     * 预读一个token之后缓存到TextScanner上
     */
    public void setPeekToken(IToken peekToken, int peekTokenLen) {
        this.peekTokenLen = peekTokenLen;
        this.peekToken = peekToken;
    }

    public void consumePeekToken() {
        peekToken = null;
        next(peekTokenLen); // 跳过占位符号
    }

    public boolean isRangePosition() {
        return rangePosition;
    }

    public TextScanner rangePosition(boolean rangePosition) {
        this.rangePosition = rangePosition;
        return this;
    }

    public static TextScanner fromString(SourceLocation loc, CharSequence str) {
        return fromReader(loc, new CharSequenceReader(str));
    }

    public static TextScanner fromReader(SourceLocation loc, ICharReader reader) {
        return new TextScanner(loc, reader);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "[reader=" + reader.currentState()).append(",loc=")
                .append(this.location());
        return sb.toString();
    }

    public NopException newError(ErrorCode code) {
        NopException e = useEvalException ? new NopEvalException(code) : new NopException(code);
        if (isEnd()) {
            e.param(ARG_EOF, true);
        }
        return e.param(ARG_READER_STATE, reader.currentState()).param(ARG_POS, pos).loc(this.location());
    }

    public NopException newError(ErrorCode code, Throwable ex) {
        return newError(code).cause(ex);
    }

    public SourceLocation location() {
        if (prevLoc.getLine() == line && prevLoc.getCol() == col)
            return prevLoc;
        prevLoc = location(line, col, 0);
        return prevLoc;
    }

    public SourceLocation location(int line, int col, int length) {
        return baseLoc.position(line, col, length);
    }

    public void beginPosition() {
        this.prevLine = this.line;
        this.prevCol = this.col;
        this.prevPos = this.pos;
    }

    public SourceLocation endPosition() {
        return baseLoc.position(prevLine, prevCol, pos - prevPos);
    }

    /**
     * 判断是否已经读到流的末尾。当到达流尾部之后，cur变量=0
     */
    public final boolean isEnd() {
        return cur < 0;
    }

    /**
     * 取{@link #cur 当前位置}的下一个字符
     */
    public int peek() {
        return reader.peek();
    }

    public boolean startsWith(CharSequence seq) {
        if (cur != seq.charAt(0))
            return false;

        int n = seq.length();
        if (n == 1)
            return true;
        if (n == 2)
            return peek() == seq.charAt(1);

        for (int i = 1; i < n; i++) {
            if (peek(i) != seq.charAt(i))
                return false;
        }
        return true;
    }

    public boolean startsWithLine(String line) {
        if (!startsWith(line))
            return false;
        int c = peek(line.length());
        if (c == '\r' || c == '\n')
            return true;
        return false;
    }

    public boolean startsWithIgnoreCase(CharSequence seq) {
        if (Character.toLowerCase(cur) != Character.toLowerCase(seq.charAt(0)))
            return false;
        int n = seq.length();
        if (n == 1)
            return true;
        if (n == 2)
            return Character.toLowerCase(peek()) == Character.toLowerCase(seq.charAt(1));

        for (int i = 1; i < n; i++) {
            if (Character.toLowerCase(peek(i)) != Character.toLowerCase(seq.charAt(i)))
                return false;
        }
        return true;
    }

    public boolean startsWithTokenIgnoreCase(CharSequence seq) {
        return startsWithIgnoreCase(seq) && !StringHelper.isJavaIdentifierPart(peek(seq.length()));
    }

    public boolean startsWithToken(CharSequence seq) {
        return startsWith(seq) && !StringHelper.isJavaIdentifierPart(peek(seq.length()));
    }

    /**
     * 取{@link #cur 当前位置}的第 N 个字符：N 为 0 时，取当前字符
     */
    public int peek(int nRead) {
        if (nRead == 0)
            return cur;
        return reader.peek(nRead);
    }

    /**
     * 读取下一个字符到cur变量中
     *
     * @return
     */
    public boolean next() {
        if (cur < 0)
            return false;

        cur = read0();
        pos++;

        if (cur < 0)
            return false;

        syncLineState();

        return true;
    }

    private void syncLineState() {
        if (cur == 0)
            throw newError(ERR_SCAN_INVALID_CHAR).param(ARG_CUR, cur);

        if (cur == '\n') {
            if (lineState == 2) {
                col = 1;
                line++;
            } else {
                col++;
            }
            lineState = 2;
        } else {
            if (lineState != 0) {
                col = 1;
                line++;
            } else {
                col++;
            }

            // lineState: 0 前一个字符为普通字符, 1 前一个字符为\r, 2 前一个字符为\n
            if (cur == '\r') {
                lineState = 1;
            } else {
                lineState = 0;
            }
        }
    }

    private int read0() {
        return reader.read();
    }

    public boolean next(int n) {
        for (int i = 0; i < n; i++) {
            if (!next())
                return false;
        }
        return true;
    }

    /**
     * 如果字符匹配，则移动到下一个字符
     *
     * @param c
     * @return
     */
    public boolean tryConsume(char c) {
        if (cur == c) {
            next();
            return true;
        }
        return false;
    }

    /**
     * 判断当前字符为期待值，并移动到下一个字符
     */
    public void consume(char c, ErrorCode errorCode) {
        if (!tryConsume(c)) {
            throw newError(errorCode).param(ARG_EXPECTED, c);
        }
    }

    public void consume(char c) {
        consume(c, ERR_SCAN_UNEXPECTED_CHAR);
    }

    public void consumeInline(char c) {
        consume(c);
        skipBlankInLine();
    }

    public boolean tryConsumeIgnoreCase(char c) {
        if (Character.toUpperCase(cur) == Character.toUpperCase(c)) {
            next();
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断当前字符为期待值，并移动到下一个字符
     */
    public void consumeIgnoreCase(char c, ErrorCode errorCode) {
        if (!tryConsume(c))
            throw newError(errorCode).param(ARG_EXPECTED, c);
    }

    public boolean tryConsume(String str) {
        if (!startsWith(str))
            return false;
        next(str.length());
        return true;
    }

    public boolean tryConsumeIgnoreCase(String str) {
        if (!startsWithIgnoreCase(str))
            return false;
        next(str.length());
        return true;
    }

    public boolean tryConsumeToken(String str) {
        if (!startsWithToken(str))
            return false;
        next(str.length());
        return true;
    }

    public void consume(String str, ErrorCode errorCode) {
        if (!tryConsume(str)) {
            throw newError(errorCode).param(ARG_EXPECTED, str);
        }
    }

    public void consume(String str) {
        consume(str, ERR_SCAN_UNEXPECTED_STR);
    }

    public void consumeIgnoreCase(String str, ErrorCode errorCode) {
        if (!tryConsumeIgnoreCase(str)) {
            throw newError(errorCode).param(ARG_EXPECTED, str);
        }
    }

    public void moveToNextToken() {
        next();
        skipBlank();
    }

    public void match(char c, ErrorCode errorCode) {
        consume(c, errorCode);
        skipBlank();
    }

    public void match(char c) {
        consume(c);
        skipBlank();
    }

    public void match(String str) {
        consume(str);
        skipBlank();
    }

    public boolean tryMatch(char c) {
        if (tryConsume(c)) {
            skipBlank();
            return true;
        } else {
            return false;
        }
    }

    public boolean tryMatch(String str) {
        if (tryConsume(str)) {
            skipBlank();
            return true;
        } else {
            return false;
        }
    }

    public boolean tryMatchLine(String str) {
        if (!startsWithLine(str))
            return false;
        next(str.length());

        if (cur == '\r')
            this.next();
        if (cur == '\n')
            this.next();
        return true;
    }

    public boolean tryMatchIgnoreCase(String str) {
        if (tryConsumeIgnoreCase(str)) {
            skipBlank();
            return true;
        } else {
            return false;
        }
    }

    public boolean tryMatchToken(String str) {
        if (tryConsumeToken(str)) {
            skipBlank();
            return true;
        } else {
            return false;
        }
    }

    public void matchToken(String str) {
        if (tryConsumeToken(str)) {
            skipBlank();
        } else {
            throw newError(ERR_SCAN_UNEXPECTED_TOKEN).param(ARG_EXPECTED, str);
        }
    }

    /**
     * 移动到第一个非空白字符处，或者到达流末尾
     **/
    public boolean skipBlank() {
        int line = this.line;
        int pos = this.pos;
        while (isWs(cur)) {
            if (!next())
                break;
        }
        lineSkipped = line != this.line;
        blankSkipped = pos != this.pos;
        return blankSkipped;
    }

    /**
     * 与 StringHelper.isSpace 相同的谓词，内联在扫描器热点循环中使用
     */
    private static boolean isWs(int ch) {
        return ch <= ' ' && (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f' || ch == '\b');
    }

    public void checkBlankSkipped() {
        if (!blankSkipped)
            throw newError(ERR_SCAN_BLANK_EXPECTED);
    }

    public void checkEndOrBlankSkipped() {
        if (!blankSkipped && !isEnd())
            throw newError(ERR_SCAN_BLANK_EXPECTED);
    }

    public void checkEnd() {
        if (!isEnd())
            throw newError(ERR_SCAN_NOT_END_PROPERLY);
    }

    /**
     * 验证前一个符号和当前token之间存在空白分隔符或者文本已经结束
     */
    public void checkTokenEnd() {
        if (!blankSkipped && !isEnd() && StringHelper.isJavaIdentifierPart(cur))
            throw newError(ERR_SCAN_TOKEN_END_EXPECTED);
    }

    public boolean startsWithSpace() {
        return StringHelper.isSpace(cur);
    }

    public void skipBlankInLine() {
        while (StringHelper.isSpaceInLine(cur)) {
            blankSkipped = true;
            if (!next())
                break;
        }
    }

    /**
     * 跳过当前行
     *
     * @param out 如果传入consumer, 则所有被跳过的字符将被收集起来。不过最后的换行符会被自动丢弃。
     */
    public void skipLine(IntConsumer out) {
        while (cur > 0) {
            if (cur == '\r') {
                next();
                if (cur == '\n') {
                    next();
                }
                break;
            }
            if (cur == '\n') {
                next();
                break;
            }
            if (out != null)
                out.accept(cur);
            next();
        }
    }

    public void skipLine() {
        skipLine(null);
    }

    public int skipEmptyLines() {
        int n = 0;
        while (nextLineIsEmpty() && cur > 0) {
            skipLine();
            n++;
        }
        return n;
    }

    private boolean nextLineIsEmpty() {
        int i = 0;
        do {
            int c = peek(i++);
            if (c < 0)
                return true;

            if (c == '\n' || c == '\r')
                return true;

            if (!StringHelper.isWhitespace((char) c))
                return false;
        } while (true);
    }

    // java语法的unescape
    private char unescapeJavaChar() {
        int c = cur;
        switch (c) {
            case '\'':
            case '"':
            case '\\':
                return (char) c;
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'u':
                // unicode
                int sValue = 0;
                for (int k = 1; k <= 4; k++) {
                    next();
                    int aChar = cur;
                    switch (aChar) {
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                            sValue = (sValue << 4) + aChar - '0';
                            break;
                        case 'a':
                        case 'b':
                        case 'c':
                        case 'd':
                        case 'e':
                        case 'f':
                            sValue = (sValue << 4) + 10 + aChar - 'a';
                            break;
                        case 'A':
                        case 'B':
                        case 'C':
                        case 'D':
                        case 'E':
                        case 'F':
                            sValue = (sValue << 4) + 10 + aChar - 'A';
                            break;
                        default:
                            throw newError(ERR_SCAN_INVALID_ESCAPE_UNICODE).param(ARG_CUR, sValue);
                    }
                }
//                if (sValue <= 255 && sValue >= 0)
//                    throw newError(ERR_SCAN_INVALID_ESCAPE_UNICODE).param(ARG_CUR, sValue);
                return (char) sValue;
            default:
                throw newError(ERR_SCAN_ILLEGAL_ESCAPE_CHAR).param(ARG_CUR, (char) cur);
        }
    }

    /**
     * 从当前位置开始读取一个整数
     *
     * @return
     */
    public int nextInt() {
        Number num = this.nextNumber();
        if (!(num instanceof Integer)) {
            throw newError(ERR_SCAN_NUMBER_NOT_INT).param(ARG_VALUE, num);
        }
        return num.intValue();
    }

    public Number nextNumberOrPercent() {
        Number n = this.nextNumber();
        if (this.cur == '%') {
            this.next();
            n = n.doubleValue() * 0.01;
        }
        return n;
    }

    private Number parseInteger(String value, int radix) {
        try {
            return Integer.parseInt(value, radix);
        } catch (Exception e) { //NOPMD - suppressed EmptyCatchBlock

        }
        try {
            return Long.parseLong(value, radix);
        } catch (Exception e) {
            throw newError(radix == 16 ? ERR_SCAN_INVALID_HEX_INT_STRING : ERR_SCAN_INVALID_LONG_STRING)
                    .param(ARG_VALUE, value);
        }
    }

    private static final long LONG_MAX_DIV_10 = Long.MAX_VALUE / 10;
    private static final int LONG_MAX_MOD_10 = (int) (Long.MAX_VALUE % 10);

    /**
     * 快路径：无前导零的纯十进制整数。仅当底层输入可寻址（CharSequence）时启用：
     * 先在序列上前瞻扫描（不触碰扫描器状态），确认"纯数字、未溢出、终止符非 . e E l L f F d D"后，
     * 再经 read0() 真实消费并按 consumeDigits 相同方式批量结算状态。
     * 任何复杂情形（溢出/小数/指数/后缀/流式输入）返回 null，状态保持原样由原逻辑处理，
     * 因此错误语义与类型规则（int 范围内 Integer 否则 Long）与原实现完全一致。
     */
    private Number tryNextDecimalInteger(boolean neg) {
        CharSequence seq = getBaseSequence();
        if (seq == null)
            return null;
        int c = cur;
        if (c < '1' || c > '9')
            return null;
        int len = seq.length();
        int start = pos;
        if (start < 0 || start >= len || seq.charAt(start) != c)
            return null;
        long value = 0;
        boolean overflow = false;
        int i = start;
        while (i < len) {
            c = seq.charAt(i);
            if (c < '0' || c > '9')
                break;
            if (!overflow) {
                int digit = c - '0';
                if (value > LONG_MAX_DIV_10 || (value == LONG_MAX_DIV_10 && digit > LONG_MAX_MOD_10)) {
                    overflow = true;
                } else {
                    value = value * 10 + digit;
                }
            }
            i++;
        }
        if (overflow)
            return null;
        if (i >= len) {
            c = -1; // EOF 终止
        } else if (c == '.' || c == 'e' || c == 'E' || c == 'l' || c == 'L'
                || c == 'f' || c == 'F' || c == 'd' || c == 'D') {
            return null;
        }

        int count = i - start;
        for (int k = 0; k < count; k++) {
            this.cur = read0();
        }
        this.pos += count;
        this.col += count;
        syncLineState();

        // 注意：不能用 cond ? Integer.valueOf(..) : Long.valueOf(..) 形式——
        // 混合装箱类型的三元表达式会被数值提升统一为 long 再装箱，恒返回 Long
        if (!neg) {
            if (value <= Integer.MAX_VALUE)
                return Integer.valueOf((int) value);
            return Long.valueOf(value);
        }
        value = -value;
        if (value >= Integer.MIN_VALUE)
            return Integer.valueOf((int) value);
        return Long.valueOf(value);
    }

    /**
     * 从当前位置是否有可能解析出一个数字。具体判断当前字符是否dot/minus和数字。
     *
     * @return
     */
    public boolean isNumberStart() {
        int c = cur;
        if (c == '.' || c == '-' || StringHelper.isDigit(c))
            return true;
        return false;
    }

    public MutableString getReusableBuffer() {
        localBuf.clear();
        return localBuf;
    }

    /**
     * 读取a.b.c这种java属性路径
     */
    public String nextJavaPropPath() {
        MutableString buf = getReusableBuffer();

        if (!StringHelper.isJavaIdentifierStart(cur)) {
            consumeToBuf(buf);
            throw newError(ERR_SCAN_INVALID_PROP_PATH).param(ARG_VALUE, buf.toString());
        }

        int state = 0;
        while (StringHelper.isJavaIdentifierPart(cur) || cur == '.') {
            consumeToBuf(buf);
            if (cur == '.') {
                if (state == 1)
                    throw newError(ERR_SCAN_INVALID_PROP_PATH).param(ARG_VALUE, buf.toString());
                state = 1;
            } else {
                state = 0;
            }
        }
        if (state == 1) {
            throw newError(ERR_SCAN_INVALID_PROP_PATH).param(ARG_VALUE, buf.toString());
        }

        return buf.toString();
    }

    public String nextConfigVar() {
        MutableString buf = getReusableBuffer();

        if (!StringHelper.isJavaIdentifierStart(cur)) {
            throw newError(ERR_SCAN_INVALID_PROP_PATH).param(ARG_VALUE, buf.toString());
        }

        consumeToBuf(buf);
        int state = 0;
        while (StringHelper.isJavaIdentifierPart(cur) || cur == '.' || cur == '-') {
            consumeToBuf(buf);
            if (cur == '.' || cur == '-') {
                if (state == 1)
                    throw newError(ERR_SCAN_INVALID_PROP_PATH).param(ARG_VALUE, buf.toString());
                state = 1;
            } else {
                state = 0;
            }
        }
        if (state == 1) {
            throw newError(ERR_SCAN_INVALID_PROP_PATH).param(ARG_VALUE, buf.toString());
        }

        return buf.toString();
    }

    public String nextGraphQLVar() {
        MutableString buf = getReusableBuffer();

        if (!isGraphQLNameStart(cur)) {
            throw newError(ERR_SCAN_INVALID_VAR).param(ARG_VALUE, buf.toString());
        }
        consumeToBuf(buf);

        while (isGraphQLNamePart(cur)) {
            consumeToBuf(buf);
        }

        return buf.toString();
    }

    /**
     * 读取一个java变量名
     */
    public String nextJavaVar() {
        MutableString buf = getReusableBuffer();

        if (!StringHelper.isJavaIdentifierStart(cur)) {
            throw newError(ERR_SCAN_INVALID_VAR).param(ARG_VALUE, buf.toString());
        }
        consumeToBuf(buf);

        while (StringHelper.isJavaIdentifierPart(cur)) {
            consumeToBuf(buf);
        }

        return buf.toString();
    }

    static boolean isWordStart(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c == '_');
    }

    static boolean isWordPart(int c) {
        return isWordStart(c) || (c >= '0' && c <= '9');
    }

    public String nextWord() {
        MutableString buf = getReusableBuffer();

        if (!isWordStart(cur)) {
            throw newError(ERR_SCAN_INVALID_VAR).param(ARG_VALUE, buf.toString());
        }
        consumeToBuf(buf);

        while (isWordPart(cur)) {
            consumeToBuf(buf);
        }

        return buf.toString();
    }

    public String nextWordPath() {
        MutableString buf = getReusableBuffer();

        if (!isWordStart(cur)) {
            throw newError(ERR_SCAN_INVALID_VAR).param(ARG_VALUE, buf.toString());
        }
        consumeToBuf(buf);

        while (isWordPart(cur) || cur == '.') {
            consumeToBuf(buf);
        }

        return buf.toString();
    }

    /**
     * 读取一个java变量名
     */
    public String nextXmlNamespace() {
        MutableString buf = getReusableBuffer();

        if (!StringHelper.isXmlNameStart(cur)) {
            throw newError(ERR_SCAN_INVALID_XML_NAME).param(ARG_VALUE, String.valueOf((char) cur));
        }

        int state = 0;
        do {
            consumeToBuf(buf);

            if (cur == ':')
                break;

            if (cur == '-' || cur == '.') {
                if (state == 1)
                    throw newError(ERR_SCAN_NOT_ALLOW_TWO_SEPARATOR_IN_XML_NAME).param(ARG_VALUE, buf.toString());
                state = 1;
            } else {
                state = 0;
            }
        } while (!isEnd() && StringHelper.isXmlNamePart(cur));

        return buf.toString();
    }

    public String nextHtmlAttrName() {
        MutableString buf = getReusableBuffer();
        // 允许vue扩展
        if (cur == ':' || cur == '@' || cur == '#') {
            consumeToBuf(buf);
        }
        _nextXmlName(buf);
        return buf.toString();
    }

    /**
     * 读取一个xml标签名或者属性名。与XML规范相比，禁止了::和..这种连续的分隔符
     */
    public String nextXmlName() {
        MutableString buf = getReusableBuffer();

        _nextXmlName(buf);
        return buf.toString();
    }

    void _nextXmlName(MutableString buf) {
        if (!StringHelper.isXmlNameStart(cur)) {
            throw newError(ERR_SCAN_INVALID_XML_NAME).param(ARG_VALUE, String.valueOf((char) cur));
        }

        int state = 0;
        do {
            consumeToBuf(buf);

            if (cur == ':' || cur == '-' || cur == '.') {
                if (state == 1)
                    throw newError(ERR_SCAN_NOT_ALLOW_TWO_SEPARATOR_IN_XML_NAME).param(ARG_VALUE, buf.toString());
                state = 1;
                continue;
            } else {
                state = 0;
            }
        } while (!isEnd() && StringHelper.isXmlNamePart(cur));
    }

    /**
     * 从当前位置开始读取一个数字。 基本按照javascript语法解析。但是禁止octal literals,并增加了2L,2F,2D这种java识别的后追 0x123表示16进制数
     *
     * @return
     */
    public Number nextNumber() {
        boolean neg = false;
        if (cur == '+') {
            next();
        } else if (cur == '-') {
            neg = true;
            next();
        }

        // 快路径：无前导零的纯十进制整数直接累加，免拼串免二次 parse
        Number fast = tryNextDecimalInteger(neg);
        if (fast != null)
            return fast;

        MutableString buf = getReusableBuffer();
        if (neg)
            buf.append('-');

        if (cur == '0') {
            next();
            if (StringHelper.isHexChar(cur))
                throw newError(ERR_TEXT_NUMBER_STARTS_WITH_ZERO);
            if (cur == 'l' || cur == 'L') {
                next();
                return 0L;
            }
            if (cur == 'f' || cur == 'F') {
                next();
                return 0F;
            }
            if (cur == 'd' || cur == 'D') {
                next();
                return 0D;
            }

            // 0x
            if (cur == 'x' || cur == 'X') {
                next();
                if (!StringHelper.isHexChar(cur))
                    throw newError(ERR_TEXT_ILLEGAL_HEX_STRING);
                do {
                    consumeToBuf(buf);
                } while (StringHelper.isHexChar(cur));

                String value = buf.toString();
                return parseInteger(value, 16);
            }
            buf.append('0');
        }

        if (consumeDigits(buf)) {
            if (cur == 'l' || cur == 'L') {
                String value = buf.toString();
                next();
                try {
                    return Long.parseLong(value);
                } catch (Exception e) {
                    throw newError(ERR_SCAN_INVALID_LONG_STRING).param(ARG_VALUE, value);
                }
            }
        }

        boolean isDouble = false;

        if (cur == '.') {
            consumeToBuf(buf);
            isDouble = true;

            if (!consumeDigits(buf))
                throw newError(ERR_SCAN_INVALID_NUMBER_STRING).param(ARG_VALUE, buf.toString());
        }

        if (cur == 'e' || cur == 'E') {
            consumeToBuf(buf);
            if (cur == '.') {
                consumeToBuf(buf);
                throw newError(ERR_SCAN_INVALID_NUMBER_STRING).param(ARG_VALUE, buf.toString());
            }

            if (cur == '+' || cur == '-') {
                consumeToBuf(buf);
            }

            if (!consumeDigits(buf)) {
                throw newError(ERR_SCAN_INVALID_NUMBER_STRING).param(ARG_VALUE, buf.toString());
            }
            isDouble = true;
        }

        String value = buf.toString();
        if (value.length() <= 0)
            throw newError(ERR_SCAN_INVALID_NUMBER_STRING).param(ARG_VALUE, buf.toString());

        if (isDouble) {
            try {
                return Double.parseDouble(value);
            } catch (Exception e) {
                throw newError(ERR_SCAN_INVALID_DOUBLE_STRING).param(ARG_VALUE, value);
            }
        }

        if (cur == 'D' || cur == 'd') {
            next();
            try {
                return Double.parseDouble(value);
            } catch (Exception e) {
                throw newError(ERR_SCAN_INVALID_DOUBLE_STRING).param(ARG_VALUE, buf.toString());
            }
        } else if (cur == 'F' || cur == 'f') {
            next();
            try {
                return Float.parseFloat(value);
            } catch (Exception e) {
                throw newError(ERR_SCAN_INVALID_FLOAT_STRING).param(ARG_VALUE, value);
            }
        } else {
            return parseInteger(value, 10);
        }
    }

    public void consumeToBuf(MutableString buf) {
        buf.append((char) cur);
        next();
    }

    boolean consumeDigits(MutableString buf) {
        int p = pos;
        int c = cur;
        while (StringHelper.isDigit(c)) {
            buf.append((char) c);
            c = read0();
            p++;
        }

        if (p != pos) {
            col += p - pos;
            pos = p;
            cur = c;
            syncLineState();
            return true;
        } else {
            return false;
        }
    }

    public String nextDoubleEscapeString() {
        char quote = (char) this.cur;
        next();
        MutableString buf = getReusableBuffer();
        nextDoubleEscapeString(quote, this::appendToBuf);
        next();
        return buf.toString();
    }

    /**
     * 转义字符出现两遍则表示转义
     *
     * @param escapeChar
     * @param consumer
     */
    public void nextDoubleEscapeString(char escapeChar, IntConsumer consumer) {
        SourceLocation loc = location();
        do {
            if (isEnd())
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            if (cur == escapeChar) {
                if (peek() == escapeChar) {
                    if (consumer != null)
                        consumer.accept(escapeChar);
                    next();
                    next();
                    continue;
                }
                break;
            }
            if (consumer != null)
                consumer.accept(cur);
            next();
        } while (true);
    }

    // public String nextJavaString(char quote, boolean allowCrlf) {
    // MutableString buf = useBuf();
    // nextJavaString(quote, allowCrlf, buf);
    // return buf.toString();
    // }

    public String nextJavaString() {
        char quote = (char) this.cur;
        MutableString buf = getReusableBuffer();

        SourceLocation loc = location();
        do {
            int c = read0();
            if (c < 0)
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            col++;
            pos++;
            if (c == '\r' || c == '\n') {
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            }

            if (c == '\\') {
                next();
                buf.append(unescapeJavaChar());
                continue;
            }
            if (c == quote) {
                next();
                break;
            }
            buf.append((char) c);
        } while (true);

        return buf.toString();
    }

    public String nextJsonString() {
        char quote = (char) this.cur;
        String fast = tryNextSimpleJsonString(quote);
        if (fast != null)
            return fast;
        return nextJsonStringSlow(quote);
    }

    /**
     * 快路径：当底层输入是可直接寻址的字符序列时，先扫描引号后首个终止符（闭引号/反斜杠/换行）。
     * 区间内无转义且以闭引号终止时，一次性切片返回，避免逐字符 append 与簿记。
     * 含转义、跨行、EOF 或底层不可寻址时返回 null 回落到逐字符路径。
     * 状态推进结果与逐字符路径逐位一致：批量跳过的内容字符均为普通字符（lineState 保持 0），
     * 最终 cur 停在闭引号上再调用一次 {@link #next()} 完成与原实现相同的收尾。
     */
    private String tryNextSimpleJsonString(char quote) {
        CharSequence seq = getBaseSequence();
        if (seq == null)
            return null;
        int len = seq.length();
        int open = this.pos;
        // 防御：外部传入已部分消费的 reader 时 pos 可能与序列索引不对齐，此时回落慢路径
        if (open < 0 || open >= len || seq.charAt(open) != this.cur)
            return null;
        int i = open + 1;
        while (i < len) {
            char c = seq.charAt(i);
            if (c == quote || c == '\\' || c == '\r' || c == '\n')
                break;
            i++;
        }
        if (i >= len || seq.charAt(i) != quote)
            return null;

        int start = open + 1;
        String ret = i == start ? "" : seq.subSequence(start, i).toString();
        // 序列只用于前瞻定位；被跳过的字符（内容+闭引号）仍通过底层 reader 消费，
        // 保证 reader 自身状态与扫描器同步。相比逐字符路径省去的是每字符簿记与缓冲区 append。
        for (int k = open; k < i; k++) {
            this.cur = read0();
        }
        // 批量结算 pos/col：跳过区间内无换行，行号不变；lineState 与慢路径一样保持不动
        this.pos = i;
        this.col += i - open;
        // 此时 cur 停在闭引号上，再走一次 next() 完成与慢路径相同的收尾
        next();
        return ret;
    }

    private String nextJsonStringSlow(char quote) {
        MutableString buf = getReusableBuffer();

        SourceLocation loc = location();
        do {
            int c = read0();
            if (c < 0)
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            col++;
            pos++;
            if (c == '\r' || c == '\n') {
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            }

            if (c == '\\') {
                next();
                if (cur == '/') {
                    buf.append('/');
                } else {
                    buf.append(unescapeJavaChar());
                }
                continue;
            }
            if (c == quote) {
                next();
                break;
            }
            buf.append((char) c);
        } while (true);

        return buf.toString();
    }

    public String nextLooseJsonString() {
        char quote = (char) this.cur;
        MutableString buf = getReusableBuffer();

        SourceLocation loc = location();
        do {
            int c = read0();
            if (c < 0)
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            col++;
            pos++;
            if (c == '\r' || c == '\n') {
                throw newError(ERR_SCAN_STRING_NOT_END).param(ARG_START_LOC, loc);
            }

            if (c == '\\') {
                next();
                if (cur == '/') {
                    buf.append('/');
                } else {
                    buf.append(unescapeJavaChar());
                }
                continue;
            }

            if (c == quote) {
                int p = peek();
                // ""name"这种情况
                if (p != ',' && p != ':' && p != '}' && p != ' ' && p != '\t' && p != '\r' && p != '\n') {
                    continue;
                }

                next();
                break;
            }
            buf.append((char) c);
        } while (true);

        return buf.toString();
    }


    public MutableString nextUntil(char c, boolean allowEnd) {
        return nextUntil(sc -> sc.cur == c, allowEnd, String.valueOf(c));
    }

    public MutableString nextUntil(char c1, char c2, boolean allowEnd) {
        return nextUntil(sc -> sc.cur == c1 && sc.peek() == c2, allowEnd, String.valueOf(c1));
    }

    public MutableString nextUntilEndOfLine() {
        return nextUntil(sc -> sc.cur == '\r' || sc.cur == '\n', true, String.valueOf('\n'));
    }


    public MutableString nextUntil(Predicate<TextScanner> predicate, boolean allowEnd, String expected) {
        MutableString buf = getReusableBuffer();
        SourceLocation loc = location();
        boolean b = nextUntil(predicate, this::appendToBuf);
        if (!b && !allowEnd)
            throw newError(ERR_SCAN_NEXT_UNTIL_UNEXPECTED_EOF).param(ARG_START_LOC, loc).param(ARG_EXPECTED, expected);
        return buf;
    }

    public MutableString nextUntil(String str, boolean allowEnd) {
        return nextUntil(sc -> sc.startsWith(str), allowEnd, str);
    }

    public void appendToBuf(int c) {
        this.localBuf.append((char) c);
    }

    public void skipUntil(char c, boolean allowEnd) {
        skipUntil(sc -> sc.cur == c, allowEnd, String.valueOf(c));
    }

    public void skipUntil(String str, boolean allowEnd) {
        skipUntil(sc -> sc.startsWith(str), allowEnd, str);
    }

    public void skipUntil(Predicate<TextScanner> predicate, boolean allowEnd, String expected) {
        SourceLocation startLoc = location();
        boolean b = nextUntil(predicate, null);
        if (!b && !allowEnd)
            throw newError(ERR_SCAN_NEXT_UNTIL_UNEXPECTED_EOF).param(ARG_START_LOC, startLoc).param(ARG_EXPECTED,
                    expected);
    }

    public boolean nextUntil(Predicate<TextScanner> predicate, IntConsumer out) {
        do {
            if (isEnd())
                return false;

            if (predicate.test(this))
                return true;

            if (out != null)
                out.accept(cur);

            next();
        } while (true);
    }

    public boolean nextUntilUnescaped(char c, IntConsumer out) {
        do {
            if (isEnd())
                return false;

            if (cur == '\\') {
                if (out != null) {
                    out.accept(cur);
                }
                next();
                if (out != null) {
                    out.accept(cur);
                }

                if (!isEnd())
                    next();
                continue;
            }

            if (cur == c)
                return true;

            if (out != null)
                out.accept(cur);

            next();
        } while (true);
    }

    public void nextUntilEnd(IntConsumer out) {
        do {
            if (isEnd())
                return;

            if (out != null)
                out.accept(cur);

            next();
        } while (true);
    }

    public MutableString nextUntilEnd() {
        MutableString buf = getReusableBuffer();
        nextUntilEnd(this::appendToBuf);
        return buf;
    }

    public String nextLine() {
        MutableString str = nextUntil(sc -> sc.cur == '\n' || sc.cur == '\r', true, "\n");
        if (cur == '\n') {
            next();
        } else if (cur == '\r') {
            if (peek() == '\n') {
                next();
                next();
            } else {
                next();
            }
        }
        return str.toString();
    }

    public void skipSlashComment(IntConsumer consumer) {
        boolean bFirst = true;
        while (cur == '/') {
            if (peek() == '/') {
                if (!bFirst && consumer != null)
                    consumer.accept('\n');
                bFirst = false;
                skipLine(consumer);
                skipBlank();
            }
        }
    }

    public void skipStarComment(IntConsumer consumer) {
        boolean bFirst = true;
        while (cur == '/') {
            if (peek() == '*') {
                SourceLocation loc = location();
                if (!bFirst && consumer != null)
                    consumer.accept('\n');
                bFirst = false;

                if (!nextUntil(buf -> buf.startsWith("*/"), consumer))
                    throw newError(ERR_SCAN_COMMENT_UNEXPECTED_EOF).param(ARG_START_LOC, loc).param(ARG_EXPECTED, "*/");
                next(2);
                skipBlank();
            }
        }
    }

    public String skipJavaComment(boolean keepComment) {
        if (keepComment) {
            MutableString buf = getReusableBuffer();
            skipJavaComment(this::appendToBuf);
            return buf.trim().toString();
        } else {
            skipJavaComment(null);
            return "";
        }
    }

    public void skipJavaComment(IntConsumer out) {
        boolean bFirst = true;

        while (cur == '/') {
            int p = peek();
            if (p == '/') {
                next(2);
                if (!bFirst && out != null)
                    out.accept('\n');
                bFirst = false;

                skipLine(out);
            } else if (p == '*') {
                SourceLocation start = location();
                next(2);
                if (!bFirst && out != null)
                    out.accept('\n');

                if (!nextUntil(buf -> buf.startsWith("*/"), out))
                    throw newError(ERR_SCAN_COMMENT_UNEXPECTED_EOF).param(ARG_START_LOC, start).param(ARG_EXPECTED,
                            "*/");
                next(2);
            } else {
                break;
            }
            skipBlank();
        }
    }

    public void checkIsDigit(int ch) {
        if (ch < '0' || ch > '9')
            throw newError(ERR_SCAN_NOT_DIGIT).param(ARG_CUR, ch);
    }

    public void checkIsHex(int ch) {
        if (ch >= '0' && ch <= '9')
            return;

        if (ch >= 'A' && ch <= 'F')
            return;
        if (ch >= 'a' && ch <= 'f')
            return;

        throw newError(ERR_SCAN_NOT_HEX_CHAR).param(ARG_CUR, ch);
    }

    public char nextDigit() {
        int ch = cur;
        checkIsDigit(ch);
        next();
        return (char) ch;
    }

    public int nextHexInt() {
        int ch = cur;
        if (ch <= 'f' && ch >= 'a') {
            ch = ch - 'a' + 'A';
        }

        checkIsHex(ch);

        next();

        if (ch >= 'A')
            return ch - 'A' + 10;

        return ch - '0';
    }

    public NopException newUnexpectedError() {
        return newError(ERR_SCAN_UNEXPECTED_CHAR).param(ARG_CUR, cur);
    }

    public void checkNotNull(Object o, ErrorCode errorCode) {
        if (o == null)
            throw newError(errorCode);
    }

    public void checkNotEmpty(String s, ErrorCode errorCode) {
        if (s == null || s.length() <= 0)
            throw newError(errorCode);
    }
}
