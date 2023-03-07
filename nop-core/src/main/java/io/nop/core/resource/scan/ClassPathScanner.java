/*
 * Copyright 2002-2020 the original author or authors.
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

// copy some code from spring's PathMatchingResourcePatternResolver

package io.nop.core.resource.scan;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.URLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

public class ClassPathScanner {
    static final Logger logger = LoggerFactory.getLogger(ClassPathScanner.class);

    private final ClassLoader classLoader;

    public ClassPathScanner(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? ClassHelper.getDefaultClassLoader() : classLoader;
    }

    public ClassPathScanner() {
        this(null);
    }

    /**
     * @param path classpath前缀,不能具体到class文件，如io/entropy
     * @return
     */
    public void scanPath(String path, BiConsumer<String, URL> consumer) {
        try {

            _findResource(path, consumer);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 根据classpath前缀寻找资源文件
     *
     * @param path classpath前缀,不能具体到class文件，如io/entropy
     */
    void _findResource(String path, BiConsumer<String, URL> consumer) throws IOException {
        Set<URL> urls = doFindAllClassPathResources(classLoader, path);

        for (URL rootDirUrl : urls) {
            if (URLHelper.isJarURL(rootDirUrl)) {
                doFindPathMatchingJarResources(rootDirUrl, path, consumer);
            } else {
                doFindPathMatchingFileResources(rootDirUrl, path, consumer);
            }
        }
    }

    protected Set<URL> doFindAllClassPathResources(ClassLoader cl, String path) throws IOException {
        Set<URL> result = new LinkedHashSet<>(16);
        Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path));
        if (resourceUrls != null) {
            while (resourceUrls.hasMoreElements()) {
                URL url = resourceUrls.nextElement();
                logger.debug("nop.resource.scan-classpath.find-url:path={},url={}", path, url);
                result.add(url);
            }
        }
        return result;
    }

    public static final String JAR_URL_PREFIX = "jar:";
    public static final String JAR_URL_SEPARATOR = "!/";
    public static final String FILE_URL_PREFIX = "file:";

    public static final String WAR_URL_SEPARATOR = "*/";

    protected void doFindPathMatchingJarResources(URL rootDirURL, String path, BiConsumer<String, URL> consumer)
            throws IOException {

        // URLConnection con = rootDirURL.openConnection();
        JarFile jarFile;
        String jarFileUrl;
        boolean closeJarFile;

        // if (con instanceof JarURLConnection) {
        // // Should usually be the case for traditional JAR files.
        // JarURLConnection jarCon = (JarURLConnection) con;
        // URLHelper.useCachesIfNecessary(jarCon);
        // jarFile = jarCon.getJarFile();
        // jarFileUrl = jarCon.getJarFileURL().toExternalForm();
        // JarEntry jarEntry = jarCon.getJarEntry();
        // closeJarFile = !jarCon.getUseCaches();
        // } else {
        // No JarURLConnection -> need to resort to URL file parsing.
        // We'll assume URLs of the format "jar:path!/entry", with the protocol
        // being arbitrary as long as following the entry format.
        // We'll also handle paths with and without leading "file:" prefix.
        String urlFile = rootDirURL.getFile();
        try {
            int separatorIndex = urlFile.indexOf(WAR_URL_SEPARATOR);
            if (separatorIndex == -1) {
                separatorIndex = urlFile.indexOf(JAR_URL_SEPARATOR);
            }
            if (separatorIndex != -1) {
                jarFileUrl = urlFile.substring(0, separatorIndex);
                jarFile = getJarFile(jarFileUrl);
            } else {
                jarFile = new JarFile(urlFile);
                jarFileUrl = urlFile;
            }
            closeJarFile = true;
        } catch (ZipException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping invalid jar classpath entry [" + urlFile + "]");
            }
            return;
        }
        // }

        try {
            if (logger.isTraceEnabled()) {
                logger.trace("Looking for matching resources in jar file [" + jarFileUrl + "]");
            }

            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                if (entryPath.endsWith("/"))
                    continue;

                if (StringHelper.startsWithPath(entryPath, path)) {
                    URL url = classLoader.getResource(entryPath);
                    if (url != null)
                        consumer.accept(entryPath, url);
                }
            }
        } finally {
            if (closeJarFile) {
                jarFile.close();
            }
        }
    }

    protected JarFile getJarFile(String jarFileUrl) throws IOException {
        if (jarFileUrl.startsWith(FILE_URL_PREFIX)) {
            return new JarFile(URLHelper.toURI(jarFileUrl).getSchemeSpecificPart());
        } else {
            return new JarFile(jarFileUrl);
        }
    }

    protected void doFindPathMatchingFileResources(URL rootUrl, String path, BiConsumer<String, URL> consumer) {

        File rootDir = URLHelper.getFile(rootUrl);
        if (rootDir != null) {
            doFindMatchingFileSystemResources(rootDir, path, consumer);
        }
    }

    protected void doFindMatchingFileSystemResources(File rootDir, String path, BiConsumer<String, URL> consumer) {
        File[] subFiles = rootDir.listFiles();
        if (subFiles != null) {
            Arrays.sort(subFiles);
            for (File subFile : subFiles) {
                String subPath = StringHelper.appendPath(path, subFile.getName());
                if (subFile.isFile()) {
                    consumer.accept(subPath, URLHelper.toURL(subFile));
                } else if (subFile.isDirectory()) {
                    doFindMatchingFileSystemResources(subFile, subPath, consumer);
                }
            }
        }
    }
}