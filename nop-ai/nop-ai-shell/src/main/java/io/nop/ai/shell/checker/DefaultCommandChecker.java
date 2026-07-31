package io.nop.ai.shell.checker;

import io.nop.ai.shell.model.Redirect;
import io.nop.ai.shell.model.SimpleCommand;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default deny-list based command checker.
 * <p>
 * Contract: return {@code null} to allow a command, return a rejection message
 * (String) to block it. The rule set is intentionally conservative: only
 * clearly destructive patterns are rejected, mirroring the semantics of
 * {@code BashExecutor.DESTRUCTIVE_COMMAND}.
 */
public class DefaultCommandChecker implements ICommandChecker {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "dd", "shutdown", "reboot", "halt", "poweroff", "init", "fdisk"
    );

    private static final Set<String> SHELL_INTERPRETERS = Set.of(
            "bash", "sh", "zsh", "dash", "ksh"
    );

    private static final Pattern MKFS_COMMAND = Pattern.compile("^mkfs(\\.\\w+)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Storage devices that must never be written to. /dev/null, /dev/zero,
     * /dev/tty and fd pseudo-files are intentionally excluded.
     */
    private static final Pattern STORAGE_DEVICE = Pattern.compile(
            "^/dev/(sd[a-z]+\\d*|hd[a-z]+\\d*|vd[a-z]+\\d*|nvme\\d+n\\d+|disk\\d+|mapper/|loop\\d+|ram\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RM_RECURSIVE_FLAG = Pattern.compile(
            "^-+([a-zA-Z]*[rR][a-zA-Z]*|.*--recursive.*)$");

    private static final Pattern RM_FORCE_FLAG = Pattern.compile(
            "^-+([a-zA-Z]*[fF][a-zA-Z]*|.*--force.*)$");

    private static final Pattern CHMOD_MODE_777 = Pattern.compile("777");

    private static boolean isRootTarget(String target) {
        return target != null && (target.equals("/") || target.startsWith("/*"));
    }

    private static boolean isStorageDevice(String target) {
        return target != null && STORAGE_DEVICE.matcher(target).find();
    }

    private static boolean hasRecursiveFlag(List<String> args) {
        for (String arg : args) {
            if (RM_RECURSIVE_FLAG.matcher(arg).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasForceFlag(List<String> args) {
        for (String arg : args) {
            if (RM_FORCE_FLAG.matcher(arg).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNoPreserveRoot(List<String> args) {
        return args.contains("--no-preserve-root");
    }

    private static String checkDeviceTargets(String command, List<String> args, List<Redirect> redirects) {
        for (String arg : args) {
            if (isStorageDevice(arg)) {
                return "command '" + command + "' writes to storage device: " + arg;
            }
        }
        for (Redirect redirect : redirects) {
            if (isStorageDevice(redirect.target())) {
                return "command '" + command + "' redirects to storage device: " + redirect.target();
            }
        }
        return null;
    }

    private static String checkRm(List<String> args) {
        boolean recursive = hasRecursiveFlag(args);
        boolean force = hasForceFlag(args);
        boolean noPreserveRoot = containsNoPreserveRoot(args);

        for (String arg : args) {
            if (isRootTarget(arg) && (recursive || noPreserveRoot) && force) {
                return "recursive force delete of filesystem root is blocked: rm " + String.join(" ", args);
            }
        }
        return null;
    }

    private static String checkChmod(List<String> args) {
        boolean recursive = hasRecursiveFlag(args);
        boolean mode777 = args.stream().anyMatch(arg -> CHMOD_MODE_777.matcher(arg).find());
        for (String arg : args) {
            if (isRootTarget(arg) && recursive && mode777) {
                return "recursive chmod 777 of filesystem root is blocked: chmod " + String.join(" ", args);
            }
        }
        return null;
    }

    private static String checkChown(List<String> args) {
        boolean recursive = hasRecursiveFlag(args);
        for (String arg : args) {
            if (isRootTarget(arg) && recursive) {
                return "recursive chown of filesystem root is blocked: chown " + String.join(" ", args);
            }
        }
        return null;
    }

    private static String checkSubCommand(String command, List<String> args, List<Redirect> redirects) {
        if (BLOCKED_COMMANDS.contains(command.toLowerCase()) || MKFS_COMMAND.matcher(command).matches()) {
            return "command is blocked by default deny-list: " + command;
        }
        if (SHELL_INTERPRETERS.contains(command) && args.isEmpty()) {
            return "bare shell interpreter (piping into " + command + ") is blocked by default deny-list";
        }
        if (command.equals("rm")) {
            String rejection = checkRm(args);
            if (rejection != null) return rejection;
        }
        if (command.equals("chmod")) {
            String rejection = checkChmod(args);
            if (rejection != null) return rejection;
        }
        if (command.equals("chown")) {
            String rejection = checkChown(args);
            if (rejection != null) return rejection;
        }
        return checkDeviceTargets(command, args, redirects);
    }

    @Override
    public String check(SimpleCommand command, ICommandCheckContext context) {
        String cmdName = command.getCommand();

        if (cmdName.equals("sudo") || cmdName.equals("doas")) {
            List<String> args = command.getArgs();
            if (!args.isEmpty()) {
                return checkSubCommand(args.get(0), args.subList(1, args.size()), command.getRedirects());
            }
            return null;
        }

        return checkSubCommand(cmdName, command.getArgs(), command.getRedirects());
    }
}
