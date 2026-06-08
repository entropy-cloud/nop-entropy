package io.nop.ai.shell.checker;

import io.nop.ai.shell.model.SimpleCommand;

public interface ICommandChecker {

    String check(SimpleCommand command, ICommandCheckContext context);
}
