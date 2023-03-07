/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.shell;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

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

    public int getId() {
        return id;
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
        this.cmds.add(cmd);
        return this;
    }

    public ShellCommand addCmds(Collection<String> cmds) {
        if (cmds != null)
            this.cmds.addAll(cmds);
        return this;
    }

    public ShellCommand addCmd(String name, String value) {
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

    public ShellCommand addEnvs(Map<String, String> envs) {
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