package io.nop.treesitter.shell;

import java.util.List;

/**
 * A script whose every command was <b>proven</b> literal: every node in the tree
 * is in the closed whitelist and every argument passed the lexical rules.
 *
 * <p>This type exists so that "we know what this script does" is a value a
 * consumer can hold, and cannot be confused with "we found some literal commands
 * somewhere in this script" — which is
 * {@link LiteralHarvest} and carries the opposite safety meaning. The two are
 * unrelated types with no shared supertype, and only this one may be used as the
 * basis of a decision.</p>
 *
 * <p>The only way to obtain one is {@link ShellAnalysis#splitProven}; a caller
 * cannot fabricate one.</p>
 */
public final class ProvenCommandSequence implements ProvenSplit {

    /**
     * One proven command: its name, its arguments, and the connector that joins it
     * to the next command in the sequence (empty for the last one).
     */
    public record Command(String name, List<String> arguments, String separator) {

        public Command {
            arguments = List.copyOf(arguments);
        }

        /** The argv form, name first. */
        public List<String> argv() {
            List<String> all = new java.util.ArrayList<>(arguments.size() + 1);
            all.add(name);
            all.addAll(arguments);
            return List.copyOf(all);
        }
    }

    private final List<Command> commands;

    ProvenCommandSequence(List<Command> commands) {
        this.commands = List.copyOf(commands);
    }

    /** The proven commands, in source order. */
    public List<Command> commands() {
        return commands;
    }

    /** The proven commands' names, in source order. */
    public List<String> commandNames() {
        return commands.stream().map(Command::name).toList();
    }

    public int size() {
        return commands.size();
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    /**
     * Always {@code true}, and deliberately so: a caller that reaches this method
     * has, by construction, a proof. It exists so that consumers do not have to
     * carry their own "did I check?" flag, and so that {@link LiteralHarvest} can
     * expose the opposite marker under a name that cannot be mistaken for it.
     */
    public boolean isProvenLiteral() {
        return true;
    }

    @Override
    public String toString() {
        return "Proven" + commands;
    }
}
