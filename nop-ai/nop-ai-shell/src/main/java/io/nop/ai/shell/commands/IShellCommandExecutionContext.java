package io.nop.ai.shell.commands;

import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;

import java.util.Map;

public interface IShellCommandExecutionContext {

    IShellInput stdin();

    IShellOutput stdout();

    IShellOutput stderr();

    Map<String, String> environment();

    String workingDirectory();

    String[] arguments();

    boolean hasFlag(String flag);

    String getFlagValue(String flag);

    String[] positionalArguments();

    IToolFileSystem fileSystem();

    ICancelToken cancelToken();
}
