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
package io.nop.commons.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import static io.nop.commons.CommonErrors.ARG_URL;
import static io.nop.commons.CommonErrors.ERR_UTILS_INVALID_URL;
import static io.nop.commons.CommonErrors.ERR_UTILS_URL_OPEN_STREAM_FAIL;

public class URLHelper {
    static final Logger LOG = LoggerFactory.getLogger(URLHelper.class);

    public static final String URL_PROTOCOL_FILE = "file";

    /**
     * URL protocol for a JBoss file system resource: "vfsfile".
     */
    public static final String URL_PROTOCOL_VFSFILE = "vfsfile";

    /**
     * URL protocol for a general JBoss VFS resource: "vfs".
     */
    public static final String URL_PROTOCOL_VFS = "vfs";

    public static URL toURL(File file) {
        try {
            return file.toURI().toURL();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public static String getCanonicalUrl(URL url) {
        if (url == null)
            return null;
        if (URLHelper.isFileURL(url)) {
            File file = URLHelper.getFile(url);
            return FileHelper.getFileUrl(file);
        }
        return url.toExternalForm();
    }

    public static boolean isFileURL(URL url) {
        if (url == null)
            return false;
        String protocol = url.getProtocol();
        return URL_PROTOCOL_FILE.equals(protocol);
        // || URL_PROTOCOL_VFSFILE.equals(protocol) ||
        // URL_PROTOCOL_VFS.equals(protocol));
    }

    public static File getFile(URL resourceUrl) {
        Guard.notNull(resourceUrl, "Resource URL must not be null");
        if (!URL_PROTOCOL_FILE.equals(resourceUrl.getProtocol())) {
            return null;
        }
        return new File(toURI(resourceUrl).getSchemeSpecificPart());
    }

    public static URI toURI(URL url) {
        String location = url.toString();
        return toURI(location);
    }

    public static URI toURI(String location) {
        try {
            return new URI(StringHelper.replace(location, " ", "%20"));
        } catch (URISyntaxException e) {
            throw new NopException(ERR_UTILS_INVALID_URL).param(ARG_URL, location);
        }
    }

    public static URL buildJarURL(File file, String path) {
        try {
            return new URL("jar:" + FileHelper.getFileUrl(file) + "!" + (path.startsWith("/") ? "" : "/") + path);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static boolean exists(URL url) {
        try {
            if (isFileURL(url)) {
                // Proceed with file system resolution
                return getFile(url).exists();
            } else {
                // Try a URL connection content-length header
                URLConnection con = url.openConnection();
                customizeConnection(con);
                HttpURLConnection httpCon = (con instanceof HttpURLConnection ? (HttpURLConnection) con : null);
                if (httpCon != null) {
                    int code = httpCon.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        return true;
                    } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return false;
                    }
                }
                if (con.getContentLengthLong() > 0) {
                    return true;
                }
                if (httpCon != null) {
                    // No HTTP OK status, and no content-length header: give up
                    httpCon.disconnect();
                    return false;
                } else {
                    // Fall back to stream existence: can we open the stream?
                    getInputStream(url).close();
                    return true;
                }
            }
        } catch (IOException ex) {
            LOG.debug("core.check_url_exists_fail", ex);
            return false;
        }
    }

    static void customizeConnection(URLConnection con) throws IOException {
        useCachesIfNecessary(con);
        if (con instanceof HttpURLConnection) {
            ((HttpURLConnection) con).setRequestMethod("HEAD");
        }
    }

    public static void useCachesIfNecessary(URLConnection con) {
        // 缺省是useCache的，这里实际上禁止了cache
        con.setUseCaches(con.getClass().getSimpleName().startsWith("JNLP"));
    }

    public static InputStream getInputStream(URL url) {
        URLConnection con = null;
        try {
            con = url.openConnection();
            useCachesIfNecessary(con);

            return con.getInputStream();
        } catch (IOException ex) {
            // Close the HTTP connection (if applicable).
            if (con instanceof HttpURLConnection) {
                ((HttpURLConnection) con).disconnect();
            }
            throw new NopException(ERR_UTILS_URL_OPEN_STREAM_FAIL, ex).param(ARG_URL, url);
        }
    }

    public static final String URL_PROTOCOL_JAR = "jar";
    public static final String URL_PROTOCOL_WAR = "war";
    public static final String URL_PROTOCOL_ZIP = "zip";
    public static final String URL_PROTOCOL_WSJAR = "wsjar";
    public static final String URL_PROTOCOL_VFSZIP = "vfszip";

    public static boolean isJarURL(URL url) {
        String protocol = url.getProtocol();
        return (URL_PROTOCOL_JAR.equals(protocol) || URL_PROTOCOL_WAR.equals(protocol)
                || URL_PROTOCOL_ZIP.equals(protocol) || URL_PROTOCOL_VFSZIP.equals(protocol)
                || URL_PROTOCOL_WSJAR.equals(protocol));
    }
}