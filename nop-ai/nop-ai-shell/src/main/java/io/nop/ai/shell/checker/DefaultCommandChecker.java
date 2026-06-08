package io.nop.ai.shell.checker;

import io.nop.ai.shell.model.SimpleCommand;

public class DefaultCommandChecker implements ICommandChecker {

    @Override
    public String check(SimpleCommand command, ICommandCheckContext context) {
        return null;
    }
}
