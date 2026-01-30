package io.nop.ai.shell.model;

import java.util.Objects;

/**
 * 重定向 - I/O重定向操作
 * 绑定到单个命令或命令组
 */
public final class Redirect {

    /**
     * 重定向操作符类型
     */
    public enum Type {
        OUTPUT(">"),           // 输出重定向（覆盖）
        APPEND(">>"),          // 输出重定向（追加）
        INPUT("<"),            // 输入重定向
        FD_OUTPUT(">&"),       // 文件描述符输出复制
        FD_INPUT("<&"),        // 文件描述符输入复制
        MERGE("&>"),           // 合并stdout和stderr（覆盖）
        MERGE_APPEND("&>>"),   // 合并stdout和stderr（追加）
        HERE_DOC("<<"),        // Here文档
        HERE_STRING("<<<");    // Here字符串

        private final String symbol;

        Type(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public static Type fromSymbol(String symbol) {
            switch (symbol) {
                case ">": return OUTPUT;
                case ">>": return APPEND;
                case "<": return INPUT;
                case ">&": return FD_OUTPUT;
                case "<&": return FD_INPUT;
                case "&>": return MERGE;
                case "&>>": return MERGE_APPEND;
                case "<<": return HERE_DOC;
                case "<<<": return HERE_STRING;
                default: throw new IllegalArgumentException("Unknown redirect symbol: " + symbol);
            }
        }
    }

    private final Integer sourceFd;
    private final Type type;
    private final String target;

    public Redirect(Integer sourceFd, Type type, String target) {
        this.sourceFd = sourceFd;
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.target = Objects.requireNonNull(target, "Target cannot be null");
    }

    /**
     * 创建输出重定向到文件（覆盖）
     */
    public static Redirect stdoutToFile(String file) {
        return new Redirect(null, Type.OUTPUT, file);
    }

    /**
     * 创建输出重定向到文件（追加）
     */
    public static Redirect stdoutAppend(String file) {
        return new Redirect(null, Type.APPEND, file);
    }

    /**
     * 创建标准输入重定向
     */
    public static Redirect stdinFromFile(String file) {
        return new Redirect(null, Type.INPUT, file);
    }

    /**
     * 创建 stderr 重定向到 stdout
     */
    public static Redirect stderrToStdout() {
        return new Redirect(2, Type.FD_OUTPUT, "1");
    }

    /**
     * 创建合并重定向（覆盖）
     */
    public static Redirect mergeToFile(String file) {
        return new Redirect(null, Type.MERGE, file);
    }

    /**
     * 创建合并重定向（追加）
     */
    public static Redirect mergeAppend(String file) {
        return new Redirect(null, Type.MERGE_APPEND, file);
    }

    /**
     * 创建文件描述符输出复制重定向
     */
    public static Redirect fdOutput(int sourceFd, int targetFd) {
        return new Redirect(sourceFd, Type.FD_OUTPUT, String.valueOf(targetFd));
    }

    /**
     * 创建文件描述符输入复制重定向
     */
    public static Redirect fdInput(int sourceFd, int targetFd) {
        return new Redirect(sourceFd, Type.FD_INPUT, String.valueOf(targetFd));
    }

    public Integer sourceFd() {
        return sourceFd;
    }

    public Type type() {
        return type;
    }

    public String target() {
        return target;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (sourceFd != null) {
            sb.append(sourceFd);
        }
        sb.append(type.symbol());

        switch (type) {
            case FD_OUTPUT:
            case FD_INPUT:
                sb.append(target);
                break;
            default:
                sb.append(" ").append(target);
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Redirect redirect = (Redirect) o;
        return Objects.equals(sourceFd, redirect.sourceFd) &&
                type == redirect.type &&
                target.equals(redirect.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFd, type, target);
    }
}
