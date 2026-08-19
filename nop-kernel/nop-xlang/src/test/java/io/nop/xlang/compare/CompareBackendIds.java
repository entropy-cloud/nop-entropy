package io.nop.xlang.compare;

/**
 * 三后端对拍矩阵的后端标识（设计 execution 01 §五）。
 */
public final class CompareBackendIds {
    public static final String INTERPRETER = "interpreter";
    public static final String JAVA = "java";
    public static final String TRUFFLE = "truffle";

    private CompareBackendIds() {
    }
}
