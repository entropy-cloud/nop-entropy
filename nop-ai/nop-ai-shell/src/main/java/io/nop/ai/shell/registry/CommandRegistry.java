package io.nop.ai.shell.registry;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

public interface CommandRegistry {

    default String name() {
        return this.getClass().getSimpleName();
    }

    Set<String> commandNames();

    Map<String, String> commandAliases();

    default boolean hasCommand(String command) {
        return commandNames().contains(command) || commandAliases().containsKey(command);
    }

    Object invoke(CommandSession session, String command, Object... args) throws Exception;

    class CommandSession {
        private final InputStream in;
        private final PrintStream out;
        private final PrintStream err;

        public CommandSession() {
            this.in = System.in;
            this.out = System.out;
            this.err = System.err;
        }

        public CommandSession(InputStream in, PrintStream out, PrintStream err) {
            this.in = in;
            this.out = out;
            this.err = err;
        }

        public InputStream in() {
            return in;
        }

        public PrintStream out() {
            return out;
        }

        public PrintStream err() {
            return err;
        }
    }
}
