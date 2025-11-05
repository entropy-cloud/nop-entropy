/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.env.PlatformEnv;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.shell.utils.ShellCommands;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ShellCommand {
    public static final String TASK_PREFIX = "@task:";

    static final AtomicInteger s_sequence = new AtomicInteger(0);

    private int id = MathHelper.nonNegativeMod(s_sequence.getAndIncrement(), 1000000);

    private List<String> cmds = new ArrayList<>();

    private boolean inheritEnv = true;
    private Map<String, String> envs = new HashMap<>();

    private String workDir;

    private long timeout;
    private Set<String> removeEnvs = new HashSet<>();

    private boolean redirectErrorStream;
    private String encoding;
    private boolean async;

    private byte[] inputBytes;
    private boolean binaryOutput;

    public ShellCommand(String... cmds) {
        this.cmds.addAll(Arrays.asList(cmds));
    }

    public ShellCommand() {
    }

    public int getId() {
        return id;
    }

    public static ShellCommand create(String command) {
        if (command.startsWith(TASK_PREFIX)) {
            return ShellCommands.task(command.substring(TASK_PREFIX.length()));
        }

        ShellCommand cmd = new ShellCommand();
        if (PlatformEnv.isWindows()) {
            cmd.addCmd("cmd");
            cmd.addCmd("/c");
        } else {
            cmd.addCmd("sh");
        }
        String[] args = splitCommandLine(command);
        for (String arg : args) {
            cmd.addCmd(arg);
        }
        return cmd;
    }


    /**
     * 将命令行字符串拆分为参数数组，支持带引号的参数和转义字符
     *
     * @param commandLine 完整的命令行字符串
     * @return 拆分后的参数数组
     */
    public static String[] splitCommandLine(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return new String[0];
        }

        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (escaped) {
                // 处理转义字符
                currentArg.append(c);
                escaped = false;
                continue;
            }

            switch (c) {
                case '\\':
                    // 遇到转义字符，标记下一个字符需要转义
                    escaped = true;
                    break;
                case '\'':
                    if (!inDoubleQuote) {
                        inSingleQuote = !inSingleQuote;
                    } else {
                        currentArg.append(c);
                    }
                    break;
                case '"':
                    if (!inSingleQuote) {
                        inDoubleQuote = !inDoubleQuote;
                    } else {
                        currentArg.append(c);
                    }
                    break;
                case ' ':
                case '\t':
                    // 空格分隔参数，除非在引号内
                    if (inSingleQuote || inDoubleQuote) {
                        currentArg.append(c);
                    } else if (currentArg.length() > 0) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                    }
                    break;
                default:
                    currentArg.append(c);
            }
        }

        // 添加最后一个参数
        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        // 检查引号是否匹配
        if (inSingleQuote || inDoubleQuote) {
            throw new IllegalArgumentException("Unclosed quote in command line: " + commandLine);
        }

        return args.toArray(new String[0]);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ShellCommand[id=").append(id);
        if (workDir != null)
            sb.append(",workDir=").append(workDir);
        sb.append(",cmd=\"").append(StringHelper.join(cmds, " ")).append("\"");
        if (envs != null && !envs.isEmpty()) {
            sb.append(",envs=").append(envs);
        }
        if (timeout > 0)
            sb.append(",timeout=").append(timeout);
        if (async)
            sb.append(",async=").append(async);
        sb.append("]");
        return sb.toString();
    }

    public String getCommandString() {
        return StringHelper.join(cmds, " ");
    }

    public ShellCommand addCmd(String cmd) {
        Guard.notEmpty(cmd, "cmd");
        this.cmds.add(cmd);
        return this;
    }

    public ShellCommand addCmds(Collection<String> cmds) {
        if (cmds != null)
            this.cmds.addAll(cmds);
        return this;
    }

    public ShellCommand addCmd(String name, String value) {
        Guard.notEmpty(name, "name");
        addCmd(name);
        addCmd(value);
        return this;
    }

    public boolean isBinaryOutput() {
        return binaryOutput;
    }

    public void setBinaryOutput(boolean binaryOutput) {
        this.binaryOutput = binaryOutput;
    }

    public ShellCommand binaryOutput(boolean binaryOutput) {
        this.binaryOutput = binaryOutput;
        return this;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public void setCmds(List<String> cmds) {
        this.cmds = cmds;
    }

    public void setInheritEnv(boolean inheritEnv) {
        this.inheritEnv = inheritEnv;
    }

    public Map<String, String> getEnvs() {
        return envs;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setRedirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
    }

    public ShellCommand async(boolean async) {
        this.async = async;
        return this;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public byte[] getInputBytes() {
        return inputBytes;
    }

    public void setInputBytes(byte[] input) {
        this.inputBytes = input;
    }

    public ShellCommand input(String input) {
        return input(input, encoding);
    }

    public void setInput(String input) {
        this.inputBytes = input.getBytes(StandardCharsets.UTF_8);
    }

    public ShellCommand input(String input, String encoding) {
        if (encoding == null) {
            encoding = StringHelper.ENCODING_UTF8;
        }
        if (input == null) {
            input = "";
        }
        try {
            this.setInputBytes(input.getBytes(encoding));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return this;
    }

    public boolean isRedirectErrorStream() {
        return redirectErrorStream;
    }

    public ShellCommand redirectErrorStream(boolean value) {
        this.redirectErrorStream = value;
        return this;
    }

    public List<String> getCmds() {
        return cmds;
    }

    public Set<String> getRemoveEnvs() {
        return removeEnvs;
    }

    public String getWorkDir() {
        return workDir;
    }

    public long getTimeout() {
        return timeout;
    }

    public ShellCommand addEnv(String name, String value) {
        envs.put(name, value);
        return this;
    }

    public ShellCommand removeEnv(String name) {
        removeEnvs.add(name);
        return this;
    }

    public ShellCommand timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public void setEnvs(Map<String, String> envs) {
        if (envs != null)
            this.envs.putAll(envs);
    }

    public ShellCommand addEnvs(Map<String, String> envs) {
        if (envs != null)
            this.envs.putAll(envs);
        return this;
    }

    public ShellCommand removeEnvs(Collection<String> envs) {
        this.removeEnvs.removeAll(envs);
        return this;
    }

    public ShellCommand workDir(String workDir) {
        this.workDir = workDir;
        return this;
    }

    public boolean isInheritEnv() {
        return inheritEnv;
    }

    public ShellCommand inheritEnv(boolean inheritEnv) {
        this.inheritEnv = inheritEnv;
        return this;
    }

}