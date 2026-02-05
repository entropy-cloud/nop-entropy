package io.nop.ai.shell.commands;

import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.resource.IResourceStore;

import java.util.Collections;import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shell命令执行上下文的默认实现
 * <p>
 * 封装了命令执行所需的所有上下文信息，包括I/O流、环境变量、工作目录和参数解析。
 * </p>
 */
public class DefaultShellExecutionContext implements IShellCommandExecutionContext {

    private final IShellInput stdin;
    private final IShellOutput stdout;
    private final IShellOutput stderr;
    private final Map<String, String> environment;
    private String workingDirectory;
    private final String[] arguments;
    private final IResourceStore resourceStore;
    private final ICancelToken cancelToken;

    private final Map<String, String> flags;
    private final String[] positionalArgs;
    /**
     * 创建默认命令执行上下文
     *
     * @param stdin 标准输入
     * @param stdout 标准输出
     * @param stderr 标准错误输出
     * @param environment 环境变量
     * @param workingDirectory 工作目录
     * @param arguments 命令行参数
     * @param resourceStore 虚拟文件系统资源存储
     */
    public DefaultShellExecutionContext(
            IShellInput stdin,
            IShellOutput stdout,
            IShellOutput stderr,
            Map<String, String> environment,
            String workingDirectory,
            String[] arguments,
            IResourceStore resourceStore) {
        this(stdin, stdout, stderr, environment, workingDirectory, arguments, resourceStore, null);
    }

    /**
     * 创建默认命令执行上下文（带取消令牌）
     *
     * @param stdin 标准输入
     * @param stdout 标准输出
     * @param stderr 标准错误输出
     * @param environment 环境变量
     * @param workingDirectory 工作目录
     * @param arguments 命令行参数
     * @param resourceStore 虚拟文件系统资源存储
     * @param cancelToken 取消令牌
     */
    public DefaultShellExecutionContext(
            IShellInput stdin,
            IShellOutput stdout,
            IShellOutput stderr,
            Map<String, String> environment,
            String workingDirectory,
            String[] arguments,
            IResourceStore resourceStore,
            ICancelToken cancelToken) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;        this.environment = environment != null ? environment : Collections.emptyMap();
        this.workingDirectory = workingDirectory != null ? workingDirectory : "/";
        this.arguments = arguments != null ? arguments : new String[0];
        this.resourceStore = resourceStore;
        this.cancelToken = cancelToken;

        this.flags = new HashMap<>();
        this.positionalArgs = parseArguments(this.arguments);    }

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
    public IResourceStore resourceStore() {
        return resourceStore;
    }

    @Override
    public ICancelToken cancelToken() {
        return cancelToken;
    }

    /**
     * 设置工作目录
     *     * @param workingDirectory 新的工作目录
     */
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * 解析命令行参数为标志和位置参数
     * <p>
     * 支持格式：
     * <ul>
     * <li>--flag=value：长格式带值</li>
     * <li>--flag：长格式布尔标志</li>
     * <li>-f：短格式标志（多个字符如 -abc 等同于 -a -b -c）</li>
     * </ul>
     * </p>
     *
     * @param args 命令行参数
     * @return 位置参数数组
     */
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
