/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.shell.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.env.PlatformEnv;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.shell.ShellCommand;
import io.nop.shell.ShellConfigs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static io.nop.shell.ShellConfigs.CFG_SHELL_TASK_ROOT_DIR;
import static io.nop.shell.ShellErrors.ARG_FILE;
import static io.nop.shell.ShellErrors.ARG_PERMS;
import static io.nop.shell.ShellErrors.ARG_TASK_NAME;
import static io.nop.shell.ShellErrors.ERR_SHELL_INVALID_TASK_NAME;
import static io.nop.shell.ShellErrors.ERR_SHELL_NOT_ALLOW_CHANGE_FILE_PERM;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;

public class ShellCommands {

    /**
     * 生成执行指定脚本文件的Shell命令对象
     *
     * @param scriptPath 待执行的脚本文件的路径
     * @return Shell命令对象
     */
    public static ShellCommand scriptFile(String scriptPath) {
        return new ShellCommand(PlatformEnv.isWindows() ? new String[]{"cmd", "/c", scriptPath}
                : new String[]{"/bin/bash", scriptPath});
    }

    /**
     * 执行{@link ShellConfigs#CFG_SHELL_TASK_ROOT_DIR}目录下的脚本任务。任务名 + 文件后缀名对应于脚本文件名。
     *
     * @param taskName 任务名
     * @return 可以通过IShellRunner执行的脚本命令对象
     */
    public static ShellCommand task(String taskName) {
        if (!StringHelper.isValidFileName(taskName))
            throw new NopException(ERR_SHELL_INVALID_TASK_NAME).param(ARG_TASK_NAME, taskName);

        String fileExt = PlatformEnv.isWindows() ? ".cmd" : ".sh";
        String filePath = StringHelper.appendPath(CFG_SHELL_TASK_ROOT_DIR.get(), taskName) + fileExt;
        return scriptFile(filePath);
    }

    // copy from Spark CommandBuilderUtils

    /**
     * Quote a command argument for a command to be run by a Windows batch script, if the argument needs quoting.
     * Arguments only seem to need quotes in batch scripts if they have certain special characters, some of which need
     * extra (and different) escaping.
     * <p>
     * For example: original single argument: ab="cde fgh" quoted: "ab^=""cde fgh"""
     */
    public static String quoteForBatchScript(String arg) {

        boolean needsQuotes = false;
        for (int i = 0; i < arg.length(); i++) {
            int c = arg.codePointAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '=' || c == ',' || c == ';') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return arg;
        }
        StringBuilder quoted = new StringBuilder();
        quoted.append("\"");
        for (int i = 0; i < arg.length(); i++) {
            int cp = arg.codePointAt(i);
            switch (cp) {
                case '"':
                    quoted.append('"');
                    break;

                default:
                    break;
            }
            quoted.appendCodePoint(cp);
        }
        if (arg.codePointAt(arg.length() - 1) == '\\') {
            quoted.append("\\");
        }
        quoted.append("\"");
        return quoted.toString();
    }

    /**
     * Quotes a string so that it can be used in a command string. Basically, just add simple escapes. E.g.: original
     * single argument : ab "cd" ef after: "ab \"cd\" ef"
     * <p>
     * This can be parsed back into a single argument by python's "shlex.split()" function.
     */
    public static String quoteForCommandString(String s) {
        StringBuilder quoted = new StringBuilder().append('"');
        for (int i = 0; i < s.length(); i++) {
            int cp = s.codePointAt(i);
            if (cp == '"' || cp == '\\') {
                quoted.appendCodePoint('\\');
            }
            quoted.appendCodePoint(cp);
        }
        return quoted.append('"').toString();
    }

    public static void permitFileExecutable(File file) {
        Path path = file.toPath();
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }

        Set<PosixFilePermission> perms = null;
        try {
            perms = view.readAttributes().permissions();
            perms.add(OWNER_EXECUTE);
            perms.add(GROUP_EXECUTE);
            view.setPermissions(perms);
        } catch (Exception e) {
            throw new NopException(ERR_SHELL_NOT_ALLOW_CHANGE_FILE_PERM, e).param(ARG_FILE, file).param(ARG_PERMS,
                    perms);
        }
    }

    /**
     * 将文本内容写入可执行的脚本文件
     *
     * @param file
     * @param script
     */
    public static void writeScriptFile(File file, String script) {
        FileHelper.writeText(file, script, StringHelper.ENCODING_UTF8);
        permitFileExecutable(file);
    }
}