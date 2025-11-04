/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.env;

import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

//从Netty项目剪裁的部分代码

public class PlatformEnv {
    static final Logger LOG = LoggerFactory.getLogger(PlatformEnv.class);

    private static final boolean IS_ANDROID = isAndroid0();
    private static final boolean IS_WINDOWS = isOSNameMatch("Windows");
    private static final boolean IS_LINUX = isOSNameMatch("Linux");
    private static final boolean IS_MAC = isOSNameMatch("Mac");

    static final int JAVA_VERSION = javaVersion0();
    static final boolean IS_OSX = isOsx0();
    static final boolean MAYBE_SUPER_USER = maybeSuperUser0();

    private static final String NORMALIZED_ARCH = normalizeArch(System.getProperty("os.arch", ""));
    private static final String NORMALIZED_OS = normalizeOs(System.getProperty("os.name", ""));

    public static boolean isAndroid() {
        return IS_ANDROID;
    }

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean isLinux() {
        return IS_LINUX;
    }

    public static boolean isMac() {
        return IS_MAC;
    }

    // copy from sofa-tracer

    /**
     * 此方法在 JDK9 下可以有更加好的方式，但是目前的几个 JDK 版本下，只能通过这个方式来搞。 在 Mac 环境下，JDK6，JDK7，JDK8 都可以跑过。 在 Linux 环境下，JDK6，JDK7，JDK8
     * 尝试过，可以运行通过。
     *
     * @return 进程 ID
     */
    public static String getPid() {
        if (isAndroid()) {
            try {
                return new File("/proc/self").getCanonicalFile().getName();
            } catch (final IOException ignoredUseDefault) {
                return StringHelper.EMPTY_STRING;
            }
        }
        return String.valueOf(java.lang.management.ManagementFactory.getRuntimeMXBean().getPid());

        /*
         * if (StringHelper.isBlank(processName)) { return StringHelper.EMPTY_STRING; }
         *
         * String[] processSplitName = processName.split("@");
         *
         * if (processSplitName.length == 0) { return StringHelper.EMPTY_STRING; }
         *
         * String pid = processSplitName[0];
         *
         * if (StringHelper.isBlank(pid)) { return StringHelper.EMPTY_STRING; }
         *
         * return pid;
         */
    }

    public static long getThreadId() {
        long tid = Thread.currentThread().getId();
        return tid;
    }

    private static boolean isOSNameMatch(String osNamePrefix) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.UK).trim();
        return StringHelper.startsWithIgnoreCase(osName, osNamePrefix);
    }

    public static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController
                    .doPrivileged((PrivilegedAction<ClassLoader>) () -> ClassLoader.getSystemClassLoader());
        }
    }

    public static ClassLoader getClassLoader(final Class<?> clazz) {
        if (System.getSecurityManager() == null) {
            return clazz.getClassLoader();
        } else {
            return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> clazz.getClassLoader());
        }
    }

    public static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController
                    .doPrivileged((PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader());
        }
    }

    /**
     * Return the version of Java under which this library is used.
     */
    public static int javaVersion() {
        return JAVA_VERSION;
    }

    private static int javaVersion0() {
        final int majorVersion;

        if (isAndroid0()) {
            majorVersion = 6;
        } else {
            majorVersion = majorVersionFromJavaSpecificationVersion();
        }

        LOG.trace("Java version: {}", majorVersion);

        return majorVersion;
    }

    // Package-private for testing only
    static int majorVersionFromJavaSpecificationVersion() {
        return majorVersion(System.getProperty("java.specification.version", "1.6"));
    }

    // Package-private for testing only
    static int majorVersion(final String javaSpecVersion) {
        final String[] components = javaSpecVersion.split("\\.");
        final int[] version = new int[components.length];
        for (int i = 0; i < components.length; i++) {
            version[i] = Integer.parseInt(components[i]);
        }

        if (version[0] == 1) {
            assert version[1] >= 6;
            return version[1];
        } else {
            return version[0];
        }
    }

    public static boolean isOsx() {
        return IS_OSX;
    }

    private static boolean isAndroid0() {
        boolean android;
        try {
            Class.forName("android.app.Application", false, getSystemClassLoader());
            android = true;
        } catch (Throwable ignored) {
            // Failed to load the class uniquely available in Android.
            android = false;
        }

        if (android) {
            LOG.debug("Platform: Android");
        }
        return android;
    }

    private static boolean isOsx0() {
        String osname = System.getProperty("os.name", "").toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
        boolean osx = osname.startsWith("macosx") || osname.startsWith("osx");

        if (osx) {
            LOG.debug("Platform: MacOS");
        }
        return osx;
    }

    private static boolean maybeSuperUser0() {
        String username = System.getProperty("user.name");
        if (isWindows()) {
            return "Administrator".equals(username);
        }
        // Check for root and toor as some BSDs have a toor user that is
        // basically the same as root.
        return "root".equals(username) || "toor".equals(username);
    }

    public static String normalizedArch() {
        return NORMALIZED_ARCH;
    }

    public static String normalizedOs() {
        return NORMALIZED_OS;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        }
        if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return "x86_32";
        }
        if (value.matches("^(ia64|itanium64)$")) {
            return "itanium_64";
        }
        if (value.matches("^(sparc|sparc32)$")) {
            return "sparc_32";
        }
        if (value.matches("^(sparcv9|sparc64)$")) {
            return "sparc_64";
        }
        if (value.matches("^(arm|arm32)$")) {
            return "arm_32";
        }
        if ("aarch64".equals(value)) {
            return "aarch_64";
        }
        if (value.matches("^(ppc|ppc32)$")) {
            return "ppc_32";
        }
        if ("ppc64".equals(value)) {
            return "ppc_64";
        }
        if ("ppc64le".equals(value)) {
            return "ppcle_64";
        }
        if ("s390".equals(value)) {
            return "s390_32";
        }
        if ("s390x".equals(value)) {
            return "s390_64";
        }

        return "unknown";
    }

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith("aix")) {
            return "aix";
        }
        if (value.startsWith("hpux")) {
            return "hpux";
        }
        if (value.startsWith("os400")) {
            // Avoid the names such as os4000
            if (value.length() <= 5 || !Character.isDigit(value.charAt(5))) {
                return "os400";
            }
        }
        if (value.startsWith("linux")) {
            return "linux";
        }
        if (value.startsWith("macosx") || value.startsWith("osx")) {
            return "osx";
        }
        if (value.startsWith("freebsd")) {
            return "freebsd";
        }
        if (value.startsWith("openbsd")) {
            return "openbsd";
        }
        if (value.startsWith("netbsd")) {
            return "netbsd";
        }
        if (value.startsWith("solaris") || value.startsWith("sunos")) {
            return "sunos";
        }
        if (value.startsWith("windows")) {
            return "windows";
        }

        return "unknown";
    }
}