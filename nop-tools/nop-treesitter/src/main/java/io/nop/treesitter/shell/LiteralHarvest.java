package io.nop.treesitter.shell;

import java.util.List;

/**
 * Every command in a script whose name and arguments are literal, found by
 * descending through control flow, pipelines and substitutions.
 *
 * <p><b>This is not a proof and must never be used as one.</b> The result is
 * incomplete by construction: a command whose arguments could not be read
 * literally is reported as {@link Command#partial()} rather than silently
 * shortened, because a shortened argv reads as "these are the arguments" when in
 * fact the argument list is not fully known. Use it to find commands worth
 * looking at; use {@link ProvenCommandSequence} to conclude anything.</p>
 *
 * <p>The type is deliberately unrelated to {@link ProvenCommandSequence}: it is
 * final, shares no supertype with it, and cannot be assigned where a
 * {@code ProvenCommandSequence} is required. {@link #isNotASafetyProof()} is the
 * runtime-readable form of the same statement for consumers that cross a
 * reflective or GraphQL boundary.</p>
 */
public final class LiteralHarvest {

    /**
     * One harvested command. {@code partial} means at least one argument was not
     * readable as a literal, so {@code arguments} is known to be a subset.
     */
    public record Command(String name, List<String> arguments, boolean partial, int byteOffset) {

        public Command {
            arguments = List.copyOf(arguments);
        }

        public List<String> argv() {
            List<String> all = new java.util.ArrayList<>(arguments.size() + 1);
            all.add(name);
            all.addAll(arguments);
            return List.copyOf(all);
        }
    }

    private final List<Command> commands;

    LiteralHarvest(List<Command> commands) {
        this.commands = List.copyOf(commands);
    }

    public List<Command> commands() {
        return commands;
    }

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
     * Always {@code true}. Present so that a consumer reading a harvest through a
     * dynamic or serialized boundary can see, without knowing this class, that the
     * result may not be used to conclude a script is safe.
     */
    public boolean isNotASafetyProof() {
        return true;
    }

    @Override
    public String toString() {
        return "Harvest" + commands;
    }
}
