/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import io.nop.api.core.convert.IByteArrayView;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.api.core.util.progress.IStepProgressListener;
import io.nop.api.core.util.progress.StepProgressListener;
import io.nop.commons.io.serialize.IStateSerializable;
import io.nop.commons.io.serialize.IStreamSerializer;
import io.nop.commons.io.stream.CharSequenceReader;
import io.nop.commons.io.stream.FastBufferedReader;
import io.nop.commons.io.stream.ICharReader;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.URLHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.core.resource.impl.URLResource;
import io.nop.core.resource.zip.IZipOutput;
import io.nop.core.resource.zip.IZipTool;
import io.nop.core.resource.zip.JdkZipTool;
import io.nop.core.resource.zip.ZipOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.nop.commons.CommonConfigs.CFG_IO_DEFAULT_BUF_SIZE;
import static io.nop.core.CoreErrors.ARG_MODULE_ID;
import static io.nop.core.CoreErrors.ARG_NAMESPACE;
import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_MODULE_ID;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_DIR;
import static io.nop.core.CoreErrors.ERR_RESOURCE_PATH_NOT_IN_NAMESPACE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_SAVE_FROM_STREAM_FAIL;

public class ResourceHelper {
    static final Logger LOG = LoggerFactory.getLogger(ResourceHelper.class);

    static IZipTool s_zipTool = new JdkZipTool();

    public static String getAppProvider(String path) {
        if (isNormalVirtualPath(path))
            return null;

        // 必须是目录部分
        int pos = path.indexOf('/', 1);
        if (pos < 0)
            return null;

        return path.substring(1, pos);
    }

    /**
     * 对于/xapp/oa/_sys 返回 xapp/oa
     */
    public static String getModuleId(String path) {
        path = getStdPath(path);

        return getModuleIdFromStdPath(path);
    }

    public static String getModuleIdFromStdPath(String path) {
        if (path.indexOf(':') > 0)
            return null;
        if (path.length() <= 1)
            return null;

        int pos = path.indexOf('/', 1);
        if (pos < 0)
            return null;
        int pos2 = path.indexOf('/', pos + 1);
        if (pos2 < 0)
            return path;
        return path.substring(1, pos2);
    }

    public static String genDayRandPath() {
        LocalDate date = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append(date.getYear()).append('/');
        int month = date.getMonthValue();
        if (month < 10) {
            sb.append('0');
        }
        sb.append(month);
        sb.append('/');
        int day = date.getDayOfMonth();
        if (day < 10)
            sb.append('0');
        sb.append(day);
        sb.append('/');
        sb.append(StringHelper.generateUUID());
        return sb.toString();
    }

    public static IResource getTempResource() {
        return getTempResource("");
    }

    public static IResource getTempResource(String prefix) {
        String path = StringHelper.appendPath(prefix, genDayRandPath());
        return VirtualFileSystem.instance().getResource(ResourceConstants.TEMP_NS + ":/" + path);
    }

    public static boolean isValidModuleId(String moduleId) {
        if (moduleId.startsWith("/"))
            return false;
        int pos = moduleId.indexOf('/');
        if (pos < 0)
            return false;
        String provider = moduleId.substring(0, pos);
        if (!StringHelper.isValidSimpleVarName(provider))
            return false;
        String moduleName = moduleId.substring(pos + 1);
        if (!StringHelper.isValidSimpleVarName(moduleName))
            return false;
        return true;
    }

    public static void checkValidModuleId(String moduleId) {
        if (!isValidModuleId(moduleId))
            throw new NopException(ERR_RESOURCE_INVALID_MODULE_ID).param(ARG_MODULE_ID, moduleId);
    }

    public static void checkValidModuleIds(Collection<String> ids) {
        for (String id : ids) {
            if (!isValidModuleId(id))
                throw new NopException(ERR_RESOURCE_INVALID_MODULE_ID).param(ARG_MODULE_ID, id);
        }
    }

    public static String getPathNamespace(String path) {
        int pos = path.indexOf(':');
        if (pos < 0)
            return null;
        return path.substring(0, pos);
    }

    public static String getCpPath(String path) {
        if (startsWithNamespace(path, ResourceConstants.CP_NS))
            return path;
        return ResourceConstants.CP_NS + ':' + path + ResourceConstants.CP_PATH_SUFFIX;
    }

    /**
     * 根据当前路径构造一个{@link VirtualFileSystem}可以识别的标准路径
     */
    public static String resolveRelativeStdPath(String currentPath, String relativePath) {
        if (currentPath == null) {
            return relativePath;
        }
        return getStdPath(StringHelper.absolutePath(currentPath, relativePath));
    }

    public static boolean startsWithNamespace(String path, String ns) {
        if (!path.startsWith(ns))
            return false;
        if (path.length() <= ns.length() + 1)
            return false;
        if (path.charAt(ns.length()) != ':')
            return false;
        return true;
    }

    public static String removeNamespace(String path, String ns) {
        if (!startsWithNamespace(path, ns))
            throw new NopException(ERR_RESOURCE_PATH_NOT_IN_NAMESPACE).param(ARG_RESOURCE_PATH, path)
                    .param(ARG_NAMESPACE, ns);
        return path.substring(ns.length() + 1);
    }

    /**
     * 判断是否普通虚拟路径。它必须不包含..或者/./这样的相对路径，以/为起始字符，且不是以/_为起始的系统资源，例如不是delta路径，不是tenant路径等。
     *
     * @param path 资源路径
     */
    public static boolean isNormalVirtualPath(String path) {
        if (path == null || !path.startsWith("/"))
            return false;

        int pos = path.indexOf(":");
        if (pos >= 0)
            return false;
//        if (path.startsWith(ResourceConstants.INTERNAL_PATH_PREFIX))
//            return false;

        if (path.endsWith("/") && !path.equals("/"))
            return false;

        return StringHelper.isCanonicalFilePath(path);
    }

    public static void checkNormalVirtualPath(String path) {
        if (!isNormalVirtualPath(path))
            throw new NopException(ERR_RESOURCE_INVALID_PATH).param(ARG_RESOURCE_PATH, path);
    }

    public static String buildNamespacePath(String ns, String path) {
        return ns + ':' + path;
    }

    public static String buildDeltaPath(String deltaLayerId, String path) {
        return ResourceConstants.DELTA_PATH_PREFIX + deltaLayerId + path;
    }

    public static String buildTenantPath(String tenantId, String path) {
        return ResourceConstants.TENANT_PATH_PREFIX + tenantId + path;
    }

    public static boolean isDeltaPath(String path) {
        return path.startsWith(ResourceConstants.DELTA_PATH_PREFIX);
    }

    public static boolean isTenantPath(String path) {
        return path.startsWith(ResourceConstants.TENANT_PATH_PREFIX);
    }

    /**
     * 在父目录中的子路径名。如果是文件，则与getName()相同。如果是目录，则返回getName()+'/'
     *
     * @param path 资源路径
     */
    public static String getSubPath(String path) {
        int endPos = path.length();
        if (path.endsWith("/")) {
            if (path.length() == 1)
                return "";
            endPos = path.length() - 1;
        }

        int pos = path.lastIndexOf('/', endPos);
        if (pos < 0) {
            pos = 0;
        }
        int pos2 = path.indexOf(':');
        if (pos2 > pos) {
            pos = pos2 + 1;
        }
        return path.substring(pos);
    }

    public static String getName(String path) {
        int endPos = path.length();
        if (path.endsWith("/")) {
            if (path.length() == 1)
                return "";
            endPos = path.length() - 1;
        }

        int pos = path.lastIndexOf('/', endPos);
        if (pos < 0) {
            pos = -1;
        }
        int pos2 = path.indexOf(':');
        if (pos2 > pos) {
            pos = pos2;
        }
        return path.substring(pos + 1);
    }

    public static String getStdPath(String path) {
        Guard.notNull(path, "resourcePath is null");

        // 有名字空间的路径不存在定制问题
        int pos = path.indexOf(':');
        if (pos > 0)
            return path;

        if (path.startsWith(ResourceConstants.TENANT_PATH_PREFIX)) {
            // 路径格式为/_tenant/{tenantId}{stdPath}
            pos = path.indexOf('/', ResourceConstants.TENANT_PATH_PREFIX.length());
            if (pos < 0)
                return "/";
            path = path.substring(pos);
        }

        if (path.startsWith(ResourceConstants.DELTA_PATH_PREFIX)) {
            // 路径格式为profile_prefix/{name}{stdPath}
            pos = path.indexOf('/', ResourceConstants.DELTA_PATH_PREFIX.length());
            if (pos < 0)
                return "/";
            return path.substring(pos);
        } else {
            return path;
        }
    }

    public static String getDeltaLayerId(String path) {
        if (!isDeltaPath(path))
            return null;
        int pos = path.indexOf('/', ResourceConstants.DELTA_PATH_PREFIX.length());
        if (pos < 0)
            return path.substring(ResourceConstants.DELTA_PATH_PREFIX.length());
        return path.substring(ResourceConstants.DELTA_PATH_PREFIX.length(), pos);
    }

    /**
     * 当调试开关打开时，模型文件的xtends合并结果会被打印到文件中。dump文件位于dump目录下，相对路径基本与stdPath相同，
     * 只是名字空间字符:被替换为__。例如classpath:a.txt对应于/_dump/classpath__a.xml，而/nop/core/a.xml对应于 /_dump/nop/core/a.xml
     */
    public static String getDumpPath(String path) {
        String dumpPath = ResourceHelper.getStdPath(path);
        dumpPath = StringHelper.replace(dumpPath, ":", "__");
        if (!dumpPath.startsWith("/"))
            dumpPath = "/" + dumpPath;
        return buildNamespacePath(ResourceConstants.DUMP_NS, dumpPath);
    }

    public static boolean isValidRelativeName(String name) {
        if (name.length() <= 0)
            return true;
        if (name.startsWith("/"))
            return false;
        return StringHelper.isCanonicalFilePath(name);
    }

    public static String normalizePath(String path) {
        path = StringHelper.normalizePath(path);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        return path;
    }

    public static void assertDirectory(IResource resource) {
        if (resource == null || !resource.isDirectory())
            throw new NopException(ERR_RESOURCE_NOT_DIR).param(ARG_RESOURCE, resource);
    }

    public static boolean hasNamespace(String path) {
        int pos = path.indexOf(':');
        return pos > 0;
    }

    public static boolean hasNamespace(String path, String ns) {
        if (path.startsWith(ns)) {
            if (path.length() > ns.length() + 2 && path.charAt(ns.length()) == ':')
                return true;
        }
        return false;
    }

    /**
     * 返回虚拟路径的父目录路径，以/结尾
     */
    public static String getParentPath(String path) {
        if (path.equals("/"))
            return "";
        int pos = path.lastIndexOf('/');
        if (pos == path.length() - 1) {
            pos = path.lastIndexOf('/', path.length() - 2);
        }
        if (pos < 0) {
            return "";
        }
        return path.substring(0, pos + 1);
    }

    public static IResource getSibling(IResource resource, String relativeName) {
        return resolveSibling(resource, relativeName);
    }

    public static IResource getSiblingWithExt(IResource resource, String ext) {
        return resolveSiblingWithExt(resource, ext);
    }

    /**
     * 相对于资源的父目录解析得到资源文件。例如resource=/a/b.txt, relativeName=c/d.txt, 则得到/a/c/d.txt
     *
     * @param resource     资源文件
     * @param relativeName 相对文件名
     */
    public static IResource resolveSibling(IResource resource, String relativeName) {
        relativeName = StringHelper.normalizePath(relativeName);
        String path = StringHelper.filePath(resource.getPath());
        path = StringHelper.appendPath(path, relativeName);

        if (hasNamespace(path, ResourceConstants.FILE_NS)) {
            path = path.substring(ResourceConstants.FILE_NS.length() + 1);
            return new FileResource(new File(path));
        }

        return VirtualFileSystem.instance().getResource(path);
    }

    public static IResource resolve(String path) {
        return VirtualFileSystem.instance().getResource(path);
    }

    public static IResource getRelatedResource(IResource resource, String postfix) {
        String name = StringHelper.fileNameNoExt(resource.getName()) + postfix;
        return ResourceHelper.getSibling(resource, name);
    }

    public static IResource resolveSiblingWithExt(IResource resource, String ext) {
        String fileName = StringHelper.fileNameNoExt(resource.getName()) + "." + ext;
        return resolveSibling(resource, fileName);
    }

    public static ICharReader toCharReader(IResource resource, String encoding) {
        if (resource instanceof InMemoryTextResource)
            return new CharSequenceReader(((InMemoryTextResource) resource).getText());
        return new FastBufferedReader(resource.getReader(encoding));
    }

    public static TextScanner buildTextScanner(IResource resource, String encoding) {
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());
        ICharReader reader = toCharReader(resource, encoding);
        return TextScanner.fromReader(loc, reader);
    }

    public static Reader toReader(IResource resource, String encoding) {
        return toReader(resource, encoding, false);
    }

    public static Reader toReader(IResource resource, String encoding, boolean supportZip) {
        return toReader(resource, encoding, supportZip, false);
    }

    public static IZipTool getZipTool() {
        return s_zipTool;
    }

    public static void registerZipTool(IZipTool zipTool) {
        s_zipTool = zipTool;
    }

    public static void zipDir(IFile dir, IResource target, ZipOptions options) {
        IZipTool tool = getZipTool();

        if (options == null) {
            options = new ZipOptions();
            if (target.getName().endsWith(".jar")) {
                options.setJarFile(true);
            }
        }

        tool.zipDirTo(dir, target, options, null);
    }

    public static void zipDirToStream(IFile dir, OutputStream target, ZipOptions options) {
        IZipTool tool = getZipTool();
        IZipOutput output = tool.newZipOutput(target, options);
        try {
            output.addDir("", dir);
            output.close();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static void unzipToDir(IResource resource, IFile dir, ZipOptions options) {
        IZipTool tool = getZipTool();
        tool.unzipToDir(resource, dir, options, null);
    }

    public static void unzip(IFile resource) {
        unzipToDir(resource, (IFile) getSibling(resource, StringHelper.removeFileExt(resource.getName())), null);
    }

    public static Reader toReader(IResource resource, String encoding, boolean supportZip, boolean disableBuffer) {
        if (encoding == null)
            encoding = ResourceConstants.ENCODING_UTF8;
        InputStream is = resource.getInputStream();
        try {
            if (supportZip && isZipFile(resource)) {
                is = new GZIPInputStream(is, CFG_IO_DEFAULT_BUF_SIZE.get());
                return new InputStreamReader(is, encoding);
            } else {
                Reader rd = new InputStreamReader(is, encoding);
                if (disableBuffer)
                    return rd;
                return new FastBufferedReader(rd);
            }
        } catch (Exception e) {
            IoHelper.safeClose(is);
            throw NopException.adapt(e);
        }
    }

    public static Writer toWriter(IResource resource, String encoding) {
        return toWriter(resource, encoding, true);
    }

    public static Writer toWriter(IResource resource, String encoding, boolean supportZip) {
        if (encoding == null)
            encoding = ResourceConstants.ENCODING_UTF8;
        AutoCloseable io = null;
        try {
            if (supportZip && isZipFile(resource)) {
                OutputStream os = resource.getOutputStream();
                io = os;
                os = new GZIPOutputStream(os, CFG_IO_DEFAULT_BUF_SIZE.get());
                return new OutputStreamWriter(os, encoding);
            } else {
                Writer out = resource.getWriter(encoding);
                io = out;
                return out;
            }
        } catch (Exception e) {
            IoHelper.safeCloseObject(io);
            throw NopException.adapt(e);
        }
    }

    static boolean isZipFile(IResource resource) {
        return resource.getName().endsWith(".gz");
    }

    public static String readText(IResource resource) {
        return readText(resource, StringHelper.ENCODING_UTF8);
    }

    public static String readText(IResource resource, String encoding) {
        LOG.info("resource.readText:resource={},encoding={}", resource, encoding);

        Reader rd = toReader(resource, encoding);
        try {
            return IoHelper.readText(rd);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(rd);
        }
    }

    public static String readTextHeader(IResource resource, String encoding, int maxChars) {
        LOG.trace("resource.readTextHeader:resource={},encoding={},maxChars={}", resource, encoding, maxChars);

        Reader rd = toReader(resource, encoding, true, true);
        char[] buf = new char[maxChars];
        try {
            int n = rd.read(buf);
            if (n < 0)
                return "";
            return new String(buf, 0, n);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(rd);
        }
    }

    public static void writeText(IResource resource, String text) {
        writeText(resource, text, null);
    }

    public static void writeText(IResource resource, String text, String encoding) {
        if (text == null)
            text = "";

        LOG.info("resource.writeText:resource={},encoding={},length={}", resource, encoding, text.length());
        LOG.trace("resource.writeText:resource={},text={}", resource, text);

        Writer out = toWriter(resource, encoding);
        try {
            out.write(text);
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(out);
        }
    }

    public static byte[] readBytes(IResource resource) {
        LOG.info("resource.readBytes:resource={}", resource);

        if (resource instanceof IByteArrayView) {
            return ((IByteArrayView) resource).toByteArray();
        }

        InputStream is = resource.getInputStream();
        try {
            long length = resource.length();
            if (length == 0)
                return StringHelper.EMPTY_BYTES;
            if (length > 0) {
                byte[] data = new byte[(int) length];
                IoHelper.readFully(is, data);
                return data;
            }
            return IoHelper.readBytes(is);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(is);
        }
    }

    public static void writeBytes(IResource resource, byte[] bytes) {
        LOG.info("resource.writeBytes:resource={},bytesLen={}", resource, bytes.length);

        OutputStream os = resource.getOutputStream();
        try {
            os.write(bytes);
            os.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    public static Properties readProperties(IResource resource) {
        LOG.info("resource.readProperties:resource={}", resource);

        Properties props = new Properties();
        InputStream is = resource.getInputStream();
        try {
            props.load(is);
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(is);
        }
        return props;
    }

    public static void writeProperties(IResource resource, Properties props) {
        LOG.info("resource.writeProperties:resource={}", resource);
        LOG.trace("resource.writeProperties:resource={},props={}", resource, props);

        OutputStream os = resource.getOutputStream();
        try {
            props.store(os, "");
            os.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    public static Object readJson(IResource resource) {
        return readJson(resource, null);
    }

    public static Object readJson(IResource resource, Type type) {
        return JsonTool.parseBeanFromResource(resource, type);
    }

    public static void writeJson(IResource resource, Object obj, String encoding, String indent) {
        LOG.info("resource.writeJson:resource={}", resource);

        Writer out = toWriter(resource, encoding);
        try {
            out = IoHelper.toBufferedWriter(out);
            JsonTool.instance().serialize(obj, indent, out);
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(out);
        }
    }

    public static void writeJson(IResource resource, Object obj) {
        writeJson(resource, obj, StringHelper.ENCODING_UTF8, "  ");
    }

    public static XNode readXml(IResource resource) {
        return readXml(resource, null);
    }

    public static XNode readXml(IResource resource, String encoding) {
        return readXml(resource, encoding, false, false);
    }

    public static XNode readXml(IResource resource, String encoding, boolean forHtml, boolean keepComment) {
        return XNodeParser.instance().defaultEncoding(encoding).forHtml(forHtml).keepComment(keepComment)
                .parseFromResource(resource);
    }

    public static void writeXml(IResource resource, XNode node, String encoding, boolean indent) {
        LOG.info("resource.writeXml:resource={},node={}", resource, node);

        node.saveToResource(resource, encoding, indent, true, false, false);
    }

    public static void writeXml(IResource resource, XNode node) {
        writeXml(resource, node, StringHelper.ENCODING_UTF8, true);
    }

    public static Object readObject(IResource resource) {
        return readObject(resource, IoHelper.streamSerializer());
    }

    public static Object readObject(IResource resource, IStreamSerializer serializer) {
        LOG.info("resource.readObject:resource={}", resource);

        InputStream is = resource.getInputStream();
        try {
            is = IoHelper.toBufferedInputStream(is);
            Object o = serializer.deserializeFromStream(is);
            return o;
        } finally {
            IoHelper.safeClose(is);
        }
    }

    public static void writeObject(IResource resource, Object obj) {
        writeObject(resource, obj, IoHelper.streamSerializer());
    }

    public static void writeObject(IResource resource, Object obj, IStreamSerializer serializer) {
        LOG.info("resource.writeObject:resource={}", resource);

        OutputStream os = resource.getOutputStream();
        try {
            os = new BufferedOutputStream(os);
            serializer.serializeToStream(obj, os);
            os.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    public static void readState(IResource resource, IStateSerializable obj) {
        readState(resource, obj, IoHelper.streamSerializer());
    }

    public static void readState(IResource resource, IStateSerializable obj, IStreamSerializer serializer) {
        LOG.info("resource.writeState:resource={}", resource);

        InputStream is = null;
        ObjectInput in = null;
        try {
            is = resource.getInputStream();
            is = IoHelper.toBufferedInputStream(is);
            in = serializer.getObjectInput(is);
            obj.state_loadFrom(in);
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(in);
            IoHelper.safeClose(is);
        }
    }

    public static void writeState(IResource resource, IStateSerializable obj) {
        writeState(resource, obj, IoHelper.streamSerializer());
    }

    public static void writeState(IResource resource, IStateSerializable obj, IStreamSerializer serializer) {
        LOG.info("resource.writeState:resource={}", resource);

        OutputStream os = null;
        ObjectOutput out = null;
        try {
            os = resource.getOutputStream();
            os = IoHelper.toBufferedOutputStream(os);
            out = serializer.getObjectOutput(os);
            obj.state_saveTo(out);
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(out);
            IoHelper.safeClose(os);
        }
    }

    public static void saveFromStream(IResource resource, InputStream is) {
        saveFromStream(resource, is, null);
    }

    public static void saveFromStream(IResource resource, InputStream is, IStepProgressListener listener) {
        LOG.info("resource.saveToStream:resource={}", resource);

        OutputStream os = null;
        try {
            os = resource.getOutputStream();
            IoHelper.copy(is, os, CFG_IO_DEFAULT_BUF_SIZE.get(), listener);
            os.flush();
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_SAVE_FROM_STREAM_FAIL, e).param(ARG_RESOURCE_PATH, resource.getPath());
        } finally {
            IoHelper.safeClose(os);
        }
    }

    public static void copyDir(IFile srcDir, IFile destDir) {
        copyDir(srcDir, destDir, null, null);
    }

    public static void copyDir(IFile srcDir, IFile destDir, Predicate<IFile> filter, IProgressListener listener) {
        List<? extends IFile> children = srcDir.getChildren();
        if (children != null) {
            for (IFile child : children) {
                if (filter != null && !filter.test(child))
                    continue;

                IFile target = destDir.getResource(child.getName());
                if (child.isDirectory()) {
                    copyDir(child, target, filter, listener);
                } else {
                    child.saveToResource(target, getStepListener(listener, "copy", child));
                }
            }
        }
    }

    public static void copy(IFile srcFile, IFile destFile, Predicate<IFile> filter, IProgressListener listener) {
        if (srcFile.isDirectory()) {
            copyDir(srcFile, destFile, filter, listener);
        } else {
            srcFile.saveToResource(destFile, getStepListener(listener, "copy", srcFile));
        }
    }

    public static void copy(IFile srcFile, IFile destFile) {
        copy(srcFile, destFile, null, null);
    }

    static StepProgressListener getStepListener(IProgressListener listener, String reason, IResource resource) {
        if (listener == null)
            return null;
        return new StepProgressListener(listener, reason + ":" + resource.getPath(), resource.length(), 0);
    }

    public static void deleteAll(IResource resource) {
        deleteAll(resource, null);
    }

    /**
     * 如果是目录，删除目录下的所有文件，最后删除目录本身
     */
    public static void deleteAll(IResource resource, IProgressListener listener) {
        if (resource instanceof IFile) {
            deleteChildren((IFile) resource, listener);
        }
        boolean deleted = resource.delete();
        if (listener != null) {
            long length = resource.length();
            listener.onProgress("delete:" + resource.getPath(), deleted ? length : 0, length);
        }
    }

    public static void deleteChildren(IFile file, IProgressListener listener) {
        List<? extends IFile> children = file.getChildren();
        if (children != null) {
            for (IFile child : children) {
                deleteAll(child, listener);
            }
        }
    }

    public static void deleteChildren(IFile file) {
        deleteChildren(file, null);
    }

    public static Predicate<IFile> filePostfixFilter(final String postfix) {
        if (postfix == null || postfix.length() <= 0)
            return null;

        return file -> file.getName().endsWith(postfix);
    }

    public static ClassPathResource getClassPathResource(Package pkg, String fileName) {
        String pkgPath = pkg.getName().replace('.', '/');
        return new ClassPathResource(ResourceConstants.CLASSPATH_NS + ':' + pkgPath + "/" + fileName);
    }

    public static ClassPathResource getClassPathResource(Class clazz) {
        String path = clazz.getName().replace('.', '/') + ".class";
        return new ClassPathResource(ResourceConstants.CLASSPATH_NS + ":" + path);
    }

    public static String buildClassPath(Package pkg, String fileName) {
        String pkgPath = pkg.getName().replace('.', '/');
        return ResourceConstants.CLASSPATH_NS + ':' + pkgPath + "/" + fileName;
    }

    public static IResource buildResourceFromURL(String path, URL url) {
        if (URLHelper.isFileURL(url)) {
            return new FileResource(path, URLHelper.getFile(url));
        } else {
            return new URLResource(path, url);
        }
    }

    public static String resolveRelativePath(String path) {
        if (StringHelper.isEmpty(path)) {
            return FileHelper.getFileUrl(FileHelper.currentDir());
        }

        if (path.startsWith("/")) {
            File file = new File(path);
            if (file.exists())
                return FileHelper.getFileUrl(file);
            return path;
        }

        if (path.startsWith("v:")) {
            return path.substring("v:".length());
        }

        if (path.startsWith(".") || path.startsWith("..")) {
            return FileHelper.getFileUrl(new File(FileHelper.currentDir(), path));
        }

        int pos = path.indexOf(':');
        if (pos < 0) {
            File file = new File(path);
            return FileHelper.getFileUrl(file);
        }
        if (pos == 1) {
            // windows path
            return FileHelper.getFileUrl(new File(path));
        }
        return path;
    }

    public static IResource resolveRelativePathResource(String path) {
        path = resolveRelativePath(path);
        return VirtualFileSystem.instance().getResource(path);
    }
}