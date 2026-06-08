package io.nop.ai.shell.io;

public class DuplexShellOutput implements IShellOutput {

    private final IShellOutput target;
    private final boolean owned;

    public DuplexShellOutput(IShellOutput target, boolean owned) {
        this.target = target;
        this.owned = owned;
    }

    public DuplexShellOutput(IShellOutput target) {
        this(target, false);
    }

    @Override
    public void write(ShellChunk chunk) {
        target.write(chunk);
    }

    @Override
    public void flush() {
        target.flush();
    }

    @Override
    public void close() {
        if (owned) target.close();
    }

    @Override
    public IShellInput asInput() {
        return target.asInput();
    }

    public IShellOutput getTarget() {
        return target;
    }
}
