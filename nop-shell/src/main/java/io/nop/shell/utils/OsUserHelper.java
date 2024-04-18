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

// copy from org.apache.dolphinscheduler.common.utils;

import io.nop.commons.env.PlatformEnv;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static io.nop.shell.ShellRunner.runCommand;

/**
 * os utils
 */
public class OsUserHelper {

    private static final Logger logger = LoggerFactory.getLogger(OsUserHelper.class);

    /**
     * Initialization regularization, solve the problem of pre-compilation performance, avoid the thread safety problem
     * of multi-thread operation
     */
    private static final Pattern PATTERN = Pattern.compile("\\s+");

    public static List<String> getUserList() {
        try {
            if (PlatformEnv.isMac()) {
                return getUserListFromMac();
            } else if (PlatformEnv.isWindows()) {
                return getUserListFromWindows();
            } else {
                return getUserListFromLinux();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    /**
     * get user list from linux
     *
     * @return user list
     */
    private static List<String> getUserListFromLinux() throws IOException {
        List<String> userList = new ArrayList<>();

        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new FileInputStream("/etc/passwd")))) {
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(":")) {
                    String[] userInfo = line.split(":");
                    userList.add(userInfo[0]);
                }
            }
        }

        return userList;
    }

    /**
     * get user list from mac
     *
     * @return user list
     */
    private static List<String> getUserListFromMac() throws IOException {
        String result = runCommand("dscl . list /users");
        if (!StringHelper.isEmpty(result)) {
            return Arrays.asList(result.split("\n"));
        }

        return Collections.emptyList();
    }

    /**
     * get user list from windows
     *
     * @return user list
     */
    private static List<String> getUserListFromWindows() throws IOException {
        String result = runCommand("net user");
        String[] lines = result.split("\n");

        int startPos = 0;
        int endPos = lines.length - 2;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }

            int count = 0;
            if (lines[i].charAt(0) == '-') {
                for (int j = 0; j < lines[i].length(); j++) {
                    if (lines[i].charAt(i) == '-') {
                        count++;
                    }
                }
            }

            if (count == lines[i].length()) {
                startPos = i + 1;
                break;
            }
        }

        List<String> users = new ArrayList<>();
        while (startPos <= endPos) {
            users.addAll(Arrays.asList(PATTERN.split(lines[startPos])));
            startPos++;
        }

        return users;
    }

    /**
     * create user
     *
     * @param userName user name
     */
    public static void createUserIfAbsent(String userName) {
        // if not exists this user, then create
        if (!getUserList().contains(userName)) {
            boolean isSuccess = createUser(userName);
            logger.info("create user {} {}", userName, isSuccess ? "success" : "fail");
        }
    }

    /**
     * create user
     *
     * @param userName user name
     * @return true if creation was successful, otherwise false
     */
    public static boolean createUser(String userName) {
        try {
            String userGroup = getGroup();
            if (StringHelper.isEmpty(userGroup)) {
                String errorLog = String.format("%s group does not exist for this operating system.", userGroup);
                logger.error(errorLog);
                return false;
            }
            if (PlatformEnv.isMac()) {
                createMacUser(userName, userGroup);
            } else if (PlatformEnv.isWindows()) {
                createWindowsUser(userName, userGroup);
            } else {
                createLinuxUser(userName, userGroup);
            }
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return false;
    }

    /**
     * create linux user
     *
     * @param userName  user name
     * @param userGroup user group
     * @throws IOException in case of an I/O error
     */
    private static void createLinuxUser(String userName, String userGroup) throws IOException {
        logger.info("create linux os user: {}", userName);
        String cmd = String.format("sudo useradd -g %s %s", userGroup, userName);
        logger.info("execute cmd: {}", cmd);
        runCommand(cmd);
    }

    /**
     * create mac user (Supports Mac OSX 10.10+)
     *
     * @param userName  user name
     * @param userGroup user group
     * @throws IOException in case of an I/O error
     */
    private static void createMacUser(String userName, String userGroup) throws IOException {
        logger.info("create mac os user: {}", userName);

        String createUserCmd = String.format("sudo sysadminctl -addUser %s -password %s", userName, userName);
        logger.info("create user command: {}", createUserCmd);
        runCommand(createUserCmd);

        String appendGroupCmd = String.format("sudo dseditgroup -o edit -a %s -t user %s", userName, userGroup);
        logger.info("append user to group: {}", appendGroupCmd);
        runCommand(appendGroupCmd);
    }

    /**
     * create windows user
     *
     * @param userName  user name
     * @param userGroup user group
     * @throws IOException in case of an I/O error
     */
    private static void createWindowsUser(String userName, String userGroup) throws IOException {
        logger.info("create windows os user: {}", userName);

        String userCreateCmd = String.format("net user \"%s\" /add", userName);
        logger.info("execute create user command: {}", userCreateCmd);
        runCommand(userCreateCmd);

        String appendGroupCmd = String.format("net localgroup \"%s\" \"%s\" /add", userGroup, userName);
        logger.info("execute append user to group: {}", appendGroupCmd);
        runCommand(appendGroupCmd);
    }

    /**
     * get system group information
     *
     * @return system group info
     * @throws IOException errors
     */
    public static String getGroup() throws IOException {
        if (PlatformEnv.isWindows()) {
            String currentProcUserName = System.getProperty("user.name");
            String result = runCommand(String.format("net user \"%s\"", currentProcUserName));
            String line = result.split("\n")[22];
            String group = PATTERN.split(line)[1];
            if (group.charAt(0) == '*') {
                return group.substring(1);
            } else {
                return group;
            }
        } else {
            String result = runCommand("groups");
            if (!StringHelper.isEmpty(result)) {
                String[] groupInfo = result.split(" ");
                return groupInfo[0];
            }
        }

        return null;
    }

    /**
     * get sudo command
     *
     * @param tenantCode tenantCode
     * @param command    command
     * @return result of sudo execute command
     */
    public static String getSudoCmd(String tenantCode, String command) {
        if (StringHelper.isEmpty(tenantCode)) {
            return command;
        }
        return String.format("sudo -u %s %s", tenantCode, command);
    }

}