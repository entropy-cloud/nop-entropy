/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.boot;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.CommonConstants;
import io.nop.commons.env.PlatformEnv;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 根据spring项目的相关代码修改
 * <p>
 * Logs application information on startup.
 *
 * @author Phillip Webb
 * @author Dave Syer
 */
class StartupInfoLogger {
    private static final Logger LOG = LoggerFactory.getLogger(StartupInfoLogger.class);

    private static final long HOST_NAME_RESOLVE_THRESHOLD = 200;

    private final Class<?> sourceClass;

    StartupInfoLogger(Class<?> sourceClass) {
        this.sourceClass = sourceClass;
    }

    private long beginTime = System.currentTimeMillis();

    void logStarting(Logger logger) {
        logger.info(getStartingMessage());
        logger.info(getRunningMessage());
        if (AppConfig.isDebugMode()) {
//            List<URL> resources = getMetaInfResources();
//            logger.info("nop.start.meta-inf-resources:\n{}", StringHelper.join(resources, "\n"));
            logger.info(getEnvInfo());
        }
    }
//
//    List<URL> getMetaInfResources() {
//        try {
//            Enumeration<URL> urls = NopApplication.class.getClassLoader().getResources("META-INF");
//            return CollectionHelper.toList(urls);
//        } catch (Exception e) {
//            LOG.trace("error", e);
//        }
//        return null;
//    }

    void logStarted(Logger logger) {
        logger.info(getStartedMessage());
    }

    private String getStartingMessage() {
        StringBuilder message = new StringBuilder();
        message.append("Starting ");
        appendApplicationName(message);
        appendVersion(message, this.sourceClass);
        appendOn(message);
        appendPid(message);
        // appendContext(message);
        return message.toString();
    }

    public String getEnvInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("starting entropy application ...\n");
        sb.append("workDir=").append(FileHelper.getAbsolutePath(FileHelper.currentDir())).append('\n');
        sb.append("user.dir=").append(new File(".").getAbsolutePath()).append("\n");
        appendEnv(sb);
        appendProperties(sb);
        appendClassPath(sb);
        return sb.toString();
    }

    private void appendEnv(StringBuilder sb) {
        sb.append("-------------env------------------\n");
        for (String name : System.getenv().keySet()) {
            sb.append(encodeValue(name, System.getenv(name))).append("\n");
        }
    }

    private void appendProperties(StringBuilder sb) {
        sb.append("\n-------------properties-------------\n");
        for (Object name : System.getProperties().keySet()) {
            sb.append(encodeValue((String) name, System.getProperty((String) name))).append("\n");
        }
    }

    private void appendClassPath(StringBuilder sb) {
        sb.append("\n-----------class path-------------\n");
        String classpath = System.getProperty("java.class.path");
        List<String> paths = StringHelper.split(classpath, File.pathSeparatorChar);
        for (String path : paths) {
            sb.append(path).append("\n");
        }
    }

    protected String encodeValue(String name, String value) {
        String lower = name.toLowerCase();
        if (lower.contains(".secret.") || lower.contains("userpass") || lower.contains("password")) {
            value = "***";
        } else if (value.startsWith(CommonConstants.SEC_VALUE_PREFIX)) {
            value = CommonConstants.SEC_VALUE_PREFIX + ":***";
        }
        return name + '=' + value;
    }

    private String getRunningMessage() {
        StringBuilder message = new StringBuilder();
        message.append("Running with Nop Entropy");
        appendVersion(message, getClass());
        return message.toString();
    }

    private String getStartedMessage() {
        long endTime = System.currentTimeMillis();
        StringBuilder message = new StringBuilder();
        message.append("Started ");
        appendApplicationName(message);
        message.append(" in ");
        message.append((endTime - beginTime) / 1000.0);
        message.append(" seconds");
        try {
            double uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
            message.append(" (JVM running for ").append(uptime).append(")");
        } catch (Throwable ex) { // NOPMD - ignore
            // No JVM time available
        }
        return message.toString();
    }

    private void appendApplicationName(StringBuilder message) {
        String name = (this.sourceClass != null) ? this.sourceClass.getSimpleName() : "application";
        message.append(name);
        message.append("(init level=").append(CoreInitialization.initializationLevel()).append(")");
    }

    private void appendVersion(StringBuilder message, Class<?> source) {
        append(message, "v", () -> source.getPackage().getImplementationVersion());
    }

    private void appendOn(StringBuilder message) {
        long startTime = System.currentTimeMillis();
        append(message, "on ", () -> InetAddress.getLocalHost().getHostName());
        long resolveTime = System.currentTimeMillis() - startTime;
        if (resolveTime > HOST_NAME_RESOLVE_THRESHOLD) {
            StringBuilder warning = new StringBuilder();
            warning.append("InetAddress.getLocalHost().getHostName() took ");
            warning.append(resolveTime);
            warning.append(" milliseconds to respond.");
            warning.append(" Please verify your network configuration");
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                warning.append(" (macOS machines may need to add entries to /etc/hosts)");
            }
            warning.append(".");

            LOG.warn(warning.toString());
        }
    }

    private void appendPid(StringBuilder message) {
        append(message, "with PID ", PlatformEnv::getPid);
    }

    private void append(StringBuilder message, String prefix, Callable<Object> call) {
        append(message, prefix, call, "");
    }

    private void append(StringBuilder message, String prefix, Callable<Object> call, String defaultValue) {
        Object result = callIfPossible(call);
        String value = (result != null) ? result.toString() : null;
        if (StringHelper.isEmpty(value)) {
            value = defaultValue;
        }
        if (!StringHelper.isEmpty(value)) {
            message.append((message.length() > 0) ? " " : "");
            message.append(prefix);
            message.append(value);
        }
    }

    private Object callIfPossible(Callable<Object> call) {
        try {
            return call.call();
        } catch (Exception ex) {
            return null;
        }
    }

}
