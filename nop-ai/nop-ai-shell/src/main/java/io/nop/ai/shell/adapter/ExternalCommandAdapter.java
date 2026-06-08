package io.nop.ai.shell.adapter;

import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.shell.model.SimpleCommand;
import io.nop.api.core.util.ICancelToken;

public class ExternalCommandAdapter {

    public int execute(SimpleCommand cmd, IShellInput stdin, IShellOutput stdout, IShellOutput stderr, ICancelToken cancelToken) {
        throw new UnsupportedOperationException("External command fallback requires nop-shell dependency. Command: " + cmd.getCommand());
    }
}
