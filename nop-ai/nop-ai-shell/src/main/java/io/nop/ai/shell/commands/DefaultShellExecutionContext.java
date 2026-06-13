package io.nop.ai.shell.commands;

import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultShellExecutionContext implements IShellCommandExecutionContext {

    private final IShellInput stdin;
    private final IShellOutput stdout;
    private final IShellOutput stderr;
    private final Map<String, String> environment;
    private String workingDirectory;
    private final String[] arguments;
    private final IToolFileSystem fileSystem;
    private final ICancelToken cancelToken;

    private final Map<String, String> flags;
    private final String[] positionalArgs;

    public DefaultShellExecutionContext(
            IShellInput stdin,
            IShellOutput stdout,
            IShellOutput stderr,
            Map<String, String> environment,
            String workingDirectory,
            String[] arguments,
            IToolFileSystem fileSystem) {
        this(stdin, stdout, stderr, environment, workingDirectory, arguments, fileSystem, null);
    }

    public DefaultShellExecutionContext(
            IShellInput stdin,
            IShellOutput stdout,
            IShellOutput stderr,
            Map<String, String> environment,
            String workingDirectory,
            String[] arguments,
            IToolFileSystem fileSystem,
            ICancelToken cancelToken) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.environment = environment != null ? environment : Collections.emptyMap();
        this.workingDirectory = workingDirectory != null ? workingDirectory : "/";
        this.arguments = arguments != null ? arguments : new String[0];
        this.fileSystem = fileSystem;
        this.cancelToken = cancelToken;

        this.flags = new HashMap<>();
        this.positionalArgs = parseArguments(this.arguments);
    }

    @Override
    public IShellInput stdin() {
        return stdin;
    }

    @Override
    public IShellOutput stdout() {
        return stdout;
    }

    @Override
    public IShellOutput stderr() {
        return stderr;
    }

    @Override
    public Map<String, String> environment() {
        return Collections.unmodifiableMap(environment);
    }

    @Override
    public String workingDirectory() {
        return workingDirectory;
    }

    @Override
    public String[] arguments() {
        return arguments;
    }

    @Override
    public boolean hasFlag(String flag) {
        return flags.containsKey(flag);
    }

    @Override
    public String getFlagValue(String flag) {
        return flags.get(flag);
    }

    @Override
    public String[] positionalArguments() {
        return positionalArgs;
    }

    @Override
    public IToolFileSystem fileSystem() {
        return fileSystem;
    }

    @Override
    public ICancelToken cancelToken() {
        return cancelToken;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    private String[] parseArguments(String[] args) {
        Map<String, String> parsedFlags = new LinkedHashMap<>();
        java.util.List<String> positional = new java.util.ArrayList<>();

        for (String arg : args) {
            if (arg.startsWith("--")) {
                int eqIndex = arg.indexOf('=');
                if (eqIndex > 2) {
                    String flag = arg.substring(2, eqIndex);
                    String value = arg.substring(eqIndex + 1);
                    parsedFlags.put(flag, value);
                } else {
                    String flag = arg.substring(2);
                    parsedFlags.put(flag, "true");
                }
            } else if (arg.startsWith("-") && arg.length() > 1) {
                String flag = arg.substring(1);
                parsedFlags.put(flag, "true");
            } else {
                positional.add(arg);
            }
        }

        this.flags.putAll(parsedFlags);
        return positional.toArray(new String[0]);
    }
}
