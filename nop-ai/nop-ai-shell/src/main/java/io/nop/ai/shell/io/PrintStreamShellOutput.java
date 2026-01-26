package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通用 PrintStream 适配器 - 复用 IShellOutput 接口实现。
 * 所有需要 PrintStream 功能的输出都使用这个类。
 */
public class PrintStreamShellOutput implements IShellOutput {

    private final PrintStream printStream;
    private final AtomicInteger writeCount = new AtomicInteger(0);

    public PrintStreamShellOutput(PrintStream printStream) {
        this.printStream = printStream;
    }

    public static PrintStreamShellOutput fromFile(File file){
        OutputStream out = null;
        try{
            out = new FileOutputStream(file);
            return new PrintStreamShellOutput(new PrintStream(out));
        }catch (IOException e){
            throw NopException.adapt(e);
        }
    }

    @Override
    public void println(String line) {
        printStream.println(line);
        writeCount.incrementAndGet();
    }

    @Override
    public void print(String str)  {
        printStream.print(str);
        writeCount.incrementAndGet();
    }

    @Override
    public void println()  {
        printStream.println();
        writeCount.incrementAndGet();
    }

    @Override
    public void flush() {
        printStream.flush();
    }

    @Override
    public void close() {
        printStream.close();
    }

    @Override
    public IShellInput asInput() {
        throw new UnsupportedOperationException("PrintStreamOutputAdapter cannot be used as input");
    }

    /**
     * 获取内部 PrintStream（用于特殊处理）
     */
    protected PrintStream getPrintStream() {
        return printStream;
    }
}
