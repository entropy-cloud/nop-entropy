package io.nop.ai.shell.registry.impl;

import io.nop.ai.shell.registry.CommandRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class AbstractCommandRegistry implements CommandRegistry {

    protected final Map<String, String> aliases = new HashMap<>();
    protected final Set<String> commands = new HashSet<>();

    @Override
    public Set<String> commandNames() {
        return new HashSet<>(commands);
    }

    @Override
    public Map<String, String> commandAliases() {
        return new HashMap<>(aliases);
    }

    protected void registerCommand(String name, String... aliasList) {
        commands.add(name);
        if (aliasList != null) {
            for (String alias : aliasList) {
                if (alias != null && !alias.isEmpty()) {
                    aliases.put(alias, name);
                }
            }
        }
    }

    protected void unregisterCommand(String name) {
        commands.remove(name);
        aliases.entrySet().removeIf(entry -> entry.getValue().equals(name));
    }

    protected String resolveAlias(String command) {
        String resolved = command;
        while (aliases.containsKey(resolved)) {
            resolved = aliases.get(resolved);
        }
        return resolved;
    }
}
