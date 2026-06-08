package io.nop.ai.shell.checker;

import java.util.Map;

public interface ICommandCheckContext {

    String workingDirectory();

    Map<String, String> environment();

    boolean isRegisteredCommand(String commandName);
}
