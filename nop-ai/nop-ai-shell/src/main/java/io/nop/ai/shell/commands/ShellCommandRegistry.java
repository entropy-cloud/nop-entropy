package io.nop.ai.shell.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shell命令注册表
 * <p>
 * 负责管理所有可执行的shell命令，支持命令注册、别名注册和命令查找。
 * 不管理命令的生命周期，仅作为命令存储和查找中心。
 * </p>
 */
public class ShellCommandRegistry {

    private final Map<String, IShellCommand> commands;
    private final Map<String, String> aliases;

    /**
     * 创建命令注册表
     */
    public ShellCommandRegistry() {
        this.commands = new LinkedHashMap<>();
        this.aliases = new HashMap<>();
    }

    /**
     * 注册命令
     *
     * @param command 要注册的命令
     */
    public void registerCommand(IShellCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        String name = command.name();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be null or empty");
        }

        commands.put(name.trim(), command);
    }

    /**
     * 注册命令别名
     *
     * @param alias 别名
     * @param commandName 原始命令名
     */
    public void registerAlias(String alias, String commandName) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Alias cannot be null or empty");
        }

        if (commandName == null || commandName.trim().isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be null or empty");
        }

        if (!commands.containsKey(commandName.trim())) {
            throw new IllegalArgumentException("Command not found: " + commandName);
        }

        aliases.put(alias.trim(), commandName.trim());
    }

    /**
     * 查找命令
     * <p>
     * 支持通过别名查找命令。如果传入的是别名，会解析为实际命令名。
     * </p>
     *
     * @param name 命令名或别名
     * @return 找到的命令，如果不存在则返回null
     */
    public IShellCommand findCommand(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String trimmedName = name.trim();

        if (commands.containsKey(trimmedName)) {
            return commands.get(trimmedName);
        }

        String actualName = aliases.get(trimmedName);
        if (actualName != null) {
            return commands.get(actualName);
        }

        return null;
    }

    /**
     * 检查命令是否存在
     *
     * @param name 命令名或别名
     * @return 如果命令或别名存在返回true，否则返回false
     */
    public boolean hasCommand(String name) {
        return findCommand(name) != null;
    }

    /**
     * 获取所有命令（不含别名）
     *
     * @return 命令映射的不可修改视图
     */
    public Map<String, IShellCommand> getAllCommands() {
        return Collections.unmodifiableMap(commands);
    }

    /**
     * 获取所有命令（含别名）
     * <p>
     * 别名会被解析为实际的命令对象。
     * </p>
     *
     * @return 命令映射的不可修改视图（包含别名）
     */
    public Map<String, IShellCommand> getAllIncludingAliases() {
        Map<String, IShellCommand> result = new LinkedHashMap<>(commands);

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            String commandName = entry.getValue();
            IShellCommand command = commands.get(commandName);
            if (command != null) {
                result.put(alias, command);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 列出所有命令名
     *
     * @return 命令名数组
     */
    public String[] listCommandNames() {
        return commands.keySet().toArray(new String[0]);
    }
}
