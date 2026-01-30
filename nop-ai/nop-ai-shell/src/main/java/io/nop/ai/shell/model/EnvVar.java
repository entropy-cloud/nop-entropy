package io.nop.ai.shell.model;

import java.util.Objects;

/**
 * 环境变量
 */
public final class EnvVar {

    /**
     * 环境变量类型
     */
    public enum Type {
        LOCAL,      // VAR=value command（局部）
        EXPORT,     // export VAR=value（导出）
        TEMP,       // 临时变量
        SYSTEM      // 系统变量（只读）
    }

    private final String name;
    private final String value;
    private final Type type;
    private final boolean expand;

    private EnvVar(String name, String value, Type type, boolean expand) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.expand = expand;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public boolean expand() {
        return expand;
    }

    /**
     * 创建局部环境变量
     */
    public static EnvVar local(String name, String value) {
        return new EnvVar(name, value, Type.LOCAL, false);
    }

    /**
     * 创建导出环境变量
     */
    public static EnvVar export(String name, String value) {
        return new EnvVar(name, value, Type.EXPORT, false);
    }

    /**
     * 创建需要展开的环境变量
     */
    public static EnvVar expand(String name, String value) {
        return new EnvVar(name, value, Type.LOCAL, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnvVar envVar = (EnvVar) o;
        return Objects.equals(name, envVar.name) &&
                Objects.equals(value, envVar.value) &&
                type == envVar.type &&
                expand == envVar.expand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, type, expand);
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
