package io.nop.ai.shell.io;

import io.nop.commons.util.IoHelper;

import java.util.ArrayList;
import java.util.List;

public class TeeOutput implements IShellOutput {

    private final List<IShellOutput> outputs;
    private final List<IShellOutput> ownedOutputs;

    public TeeOutput(IShellOutput... outputs) {
        this.outputs = new ArrayList<>();
        this.ownedOutputs = new ArrayList<>();
        for (IShellOutput output : outputs) {
            addOutput(output);
        }
    }

    public void addOutput(IShellOutput output) {
        this.outputs.add(output);
    }

    public void addOwnedOutput(IShellOutput output) {
        this.outputs.add(output);
        this.ownedOutputs.add(output);
    }

    @Override
    public void write(ShellChunk chunk) {
        for (IShellOutput output : outputs) {
            output.write(chunk);
        }
    }

    @Override
    public void flush() {
        for (IShellOutput output : outputs) {
            output.flush();
        }
    }

    @Override
    public IShellInput asInput() {
        if (!outputs.isEmpty()) {
            return outputs.get(0).asInput();
        }
        throw new UnsupportedOperationException("No output available to convert to input");
    }

    @Override
    public void close() {
        for (IShellOutput output : ownedOutputs) {
            IoHelper.safeClose(output);
        }
        outputs.clear();
        ownedOutputs.clear();
    }
}
