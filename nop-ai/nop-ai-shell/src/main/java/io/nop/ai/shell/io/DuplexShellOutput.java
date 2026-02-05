package io.nop.ai.shell.io;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件描述符复制输出
 * <p>
 * 用于实现文件描述符复制，如2>&1（将stderr重定向到stdout）
 * </p>
 */
public class DuplexShellOutput implements IShellOutput {

    private final IShellOutput target;
    private final boolean owned;
    private final AtomicInteger writeCount = new AtomicInteger(0);

    /**
     * 创建复制输出
     *
     * @param target 目标输出
     */
    public DuplexShellOutput(IShellOutput target, boolean owned) {
        this.target = target;
        this.owned = owned;
    }

    public DuplexShellOutput(IShellOutput target){
        this(target, false);
    }

    @Override
    public void print(String text) {
        target.print(text);
        writeCount.incrementAndGet();
    }

    @Override
    public void println(String text) {
        target.println(text);
        writeCount.incrementAndGet();
    }

    @Override
    public void println() {
        target.println();
        writeCount.incrementAndGet();
    }

    @Override
    public void flush() {
        target.flush();
    }

    @Override
    public void close() {
        if(owned)
            target.close();
    }

    @Override
    public IShellInput asInput() {
        return target.asInput();
    }

    /**
     * 获取目标输出
     *
     * @return 目标输出
     */
    public IShellOutput getTarget() {
        return target;
    }

    /**
     * 获取已写入的次数
     *
     * @return 已写入的次数
     */
    public int getWriteCount() {
        return writeCount.get();
    }
}
