package io.nop.ai.shell.io;

import java.io.Closeable;

/**
 * Shell 输出接口
 */
public interface IShellOutput extends Closeable {
    String EOF_MARKER = "__EOF__";

    void print(String text);

    void println(String text);

    default void println(){
        println("");
    }

    void flush();

    IShellInput asInput();

    void close();
}
