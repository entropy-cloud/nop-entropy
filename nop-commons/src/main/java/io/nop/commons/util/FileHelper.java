/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import static io.nop.commons.CommonErrors.ARG_DEST;
import static io.nop.commons.CommonErrors.ARG_PATH;
import static io.nop.commons.CommonErrors.ERR_FILE_WRITE_CONFLICT;
import static io.nop.commons.CommonErrors.ERR_IO_COPY_DEST_NOT_DIRECTORY;
import static io.nop.commons.CommonErrors.ERR_IO_COPY_DEST_NOT_FILE;
import static io.nop.commons.CommonErrors.ERR_IO_CREATE_FILE_FAIL;

public class FileHelper {
    static final Logger LOG = LoggerFactory.getLogger(FileHelper.class);
    private static File _currentDir;

    public static File currentDir() {
        if (_currentDir == null)
            _currentDir = new File(".").getAbsoluteFile();
        return _currentDir;
    }

    public static void setCurrentDir(File dir) {
        if (dir != null) {
            LOG.info("nop.file.change-current-dir:{}", dir);
        }
        _currentDir = dir;
    }

    public static byte[] readBytes(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static boolean isEmptyDir(File dir) {
        if (!dir.exists())
            return true;

        String[] names = dir.list();
        return names == null || names.length == 0;
    }

    public static void writeBytes(File file, byte[] bytes) {
        file.getParentFile().mkdirs();
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(bytes);
            os.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(os);
        }
    }

    public static String readText(File file, String encoding) {
        try {
            return Files.readString(file.toPath(), StringHelper.toCharset(encoding));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static void assureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null)
            if (!parent.exists() && !parent.mkdirs())
                LOG.warn("nop.io.file.make-dirs-fail:path={}", parent.getAbsolutePath());
    }

    public static void assureFileExists(File file) {
        assureParent(file);
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    if (!file.exists())
                        throw new NopException(ERR_IO_CREATE_FILE_FAIL)
                                .param(ARG_PATH, file.getAbsolutePath());
                }
            } catch (NopException e) {
                throw e;
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    public static void writeText(File file, String text, String encoding, boolean append) {
        assureParent(file);
        Writer out = null;
        try {
            out = IoHelper.toWriter(new FileOutputStream(file, append), encoding);
            out.write(text);
            out.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(out);
        }
    }

    public static void writeText(File file, String text, String encoding) {
        writeText(file, text, encoding, false);
    }

    public static void writeTextIfNotMatch(File file, String text, String encoding) {
        if (file.exists()) {
            String content = readText(file, encoding);
            if (Objects.equals(content, text))
                return;
        }
        writeText(file, text, encoding);
    }

    public static Properties readProperties(@Nonnull File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            Properties props = new Properties();
            props.load(is);

            return props;
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(is);
        }
    }

    public static void copyFile(@Nonnull File srcFile, @Nonnull File dstFile) {
        if (srcFile.equals(dstFile))
            return;
        prepareCopy(srcFile, dstFile, true, false);

        try {
            Files.copy(srcFile.toPath(), dstFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    public static boolean moveFile(@Nonnull File srcFile, @Nonnull File dstFile) {
        try {
            Files.move(srcFile.toPath(), dstFile.toPath());
            return true;
        } catch (FileAlreadyExistsException | DirectoryNotEmptyException e) {
            LOG.debug("nop.commons.io.move-file-fail:src={},dest={}", srcFile, dstFile, e);
            return false;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

//    public static boolean atomicMoveFile(File srcFile, File dstFile) {
//        try {
//            Files.move(srcFile.toPath(), dstFile.toPath(), StandardCopyOption.ATOMIC_MOVE,
//                    StandardCopyOption.COPY_ATTRIBUTES);
//            return true;
//        } catch (IOException outer) {
//            try {
//                Files.move(srcFile.toPath(), dstFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
//                return true;
//            } catch (FileAlreadyExistsException e) {
//                LOG.debug("nop.commons.io.atomic-move-file-fail:src={},dest={}", srcFile, dstFile, e);
//                return false;
//            } catch (DirectoryNotEmptyException e) {
//                LOG.debug("nop.commons.io.atomic-move-file-fail:src={},dest={}", srcFile, dstFile, e);
//                return false;
//
//            } catch (IOException e) {
//                throw NopException.adapt(e);
//            }
//        }
//    }

    /**
     * Creates a symbolic link at {@code link} whose target will be the {@code target}. Depending on the underlying
     * filesystem, this method may not always be able to create a symbolic link, in which case this method returns
     * {@code false}.
     *
     * @param target    The {@link File} which will be the target of the symlink being created
     * @param link      The path to the symlink that needs to be created
     * @param overwrite {@code true} if any existing file at {@code link} has to be overwritten. False otherwise
     * @return Returns true if the symlink was successfully created. Returns false if the symlink creation couldn't be
     * done
     * @throws IOException 建立符号连接失败时抛出异常
     */
    public static boolean symlink(final File target, final File link, final boolean overwrite) throws IOException {
        if (!prepareCopy(target, link, overwrite, true)) {
            return false;
        }
        Files.createSymbolicLink(link.toPath(), target.getAbsoluteFile().toPath());
        return true;
    }

    public static boolean prepareCopy(final File src, final File dest, final boolean overwrite,
                                      final boolean unlinkSymlinkIfOverwrite) {
        if (src.isDirectory()) {
            if (dest.exists()) {
                if (!dest.isDirectory()) {
                    throw new NopException(ERR_IO_COPY_DEST_NOT_DIRECTORY).param(ARG_DEST, dest);
                }
            } else {
                dest.mkdirs();
            }
            return true;
        }
        // else it is a file copy
        if (dest.exists()) {
            // If overwrite is specified as "true" and the dest file happens to
            // be a
            // symlink, we delete the "link" (a.k.a unlink it). This is for
            // cases
            // like https://issues.apache.org/jira/browse/IVY-1498 where not
            // unlinking
            // the existing symlink can lead to potentially overwriting the
            // wrong "target" file
            // TODO: This behaviour is intentionally hardcoded here for now,
            // since I don't
            // see a reason (yet) to expose it as a param of this method. If any
            // use case arises
            // we can have this behaviour decided by the callers of this method,
            // by passing a value for this
            // param
            // final boolean unlinkSymlinkIfOverwrite = true;
            if (!dest.isFile()) {
                throw new NopException(ERR_IO_COPY_DEST_NOT_FILE).param(ARG_DEST, dest);
            }
            if (overwrite) {
                if (Files.isSymbolicLink(dest.toPath()) && unlinkSymlinkIfOverwrite) {
                    // unlink (a.k.a delete the symlink path)
                    return dest.delete();
                } else if (!dest.canWrite()) {
                    // if the file *isn't* "writable" (see javadoc of
                    // File.canWrite() on what that means)
                    // we delete it.
                    return dest.delete();
                } // if dest is writable, the copy will overwrite it without
                // requiring a delete
            } else {
                return false;
            }
        }
        if (dest.getParentFile() != null) {
            dest.getParentFile().mkdirs();
        }
        return true;
    }

    public static void deleteAll(File file) {
        File[] subs = file.listFiles();
        if (subs != null) {
            for (File sub : subs) {
                deleteAll(sub);
            }
        }
        if (!file.delete())
            LOG.error("nop.file.delete-fail:file={}", file);
    }

    public static boolean deleteIfExists(File file) throws IOException {
        return Files.deleteIfExists(file.toPath());
    }

    /**
     * Reads the target of the symbolic link
     *
     * @param symlink A file that is a symlink
     * @return A file that is the target of the symlink
     * @throws java.io.IOException if IO exception occurs
     */
    public static File readSymbolicLink(File symlink) throws IOException {
        Path path = Files.readSymbolicLink(symlink.toPath());
        return path.toFile();
    }

    public static void chmod(File file, int mode) throws IOException {
        Path path = file.toPath();
        if (!Files.isSymbolicLink(path)) {
            Files.setPosixFilePermissions(path, getPermissions(mode));
        }
    }

    @SuppressWarnings({"OctalInteger", "MagicNumber"})
    private static Set<PosixFilePermission> getPermissions(int mode) {
        Set<PosixFilePermission> perms = new HashSet<>();
        // add owners permission
        if ((mode & 0x0400) > 0) {
            perms.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0x0200) > 0) {
            perms.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0x0100) > 0) {
            perms.add(PosixFilePermission.OWNER_EXECUTE);
        }
        // add group permissions
        if ((mode & 0x0040) > 0) {
            perms.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0x0020) > 0) {
            perms.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0x0010) > 0) {
            perms.add(PosixFilePermission.GROUP_EXECUTE);
        }
        // add others permissions
        if ((mode & 0x0004) > 0) {
            perms.add(PosixFilePermission.OTHERS_READ); //NOSONAR
        }
        if ((mode & 0x0002) > 0) {
            perms.add(PosixFilePermission.OTHERS_WRITE); //NOSONAR
        }
        if ((mode & 0x0001) > 0) {
            perms.add(PosixFilePermission.OTHERS_EXECUTE); //NOSONAR
        }
        return perms;
    }

    public static URL toURL(File file) {
        try {
            return file.toURI().toURL();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static File getAttachmentFile(Class<?> clazz, String attachmentFileName) {
        File file = getClassFile(clazz);
        if (file == null)
            throw new IllegalStateException("null class file");
        return new File(file.getParentFile(), attachmentFileName);
    }

    public static File getClassFile(Class<?> clazz) {
        String className = clazz.getName();
        String path = className.replace('.', '/') + ".class";

        return getClassPathFile(path);
    }

    public static String getAbsolutePath(File file) {
        return StringHelper.normalizePath(file.getAbsolutePath());
    }

    public static File getAbsoluteFile(File file) {
        return new File(getAbsolutePath(file));
    }

    // public static String getFileUrl(File file) {
    // return "file:/" + getAbsolutePath(file);
    // }

    public static File getClassPathFile(String path) {
        URL url = FileHelper.class.getClassLoader().getResource(path);
        String s = url.getFile();
        if (s == null)
            return null;
        File file = new File(s);
        return file;
    }

    public static void removeChildWithPrefix(File dir, String prefix) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.getName().startsWith(prefix))
                    if (!child.delete())
                        LOG.error("nop.err.file.remove-file-fail:file={}", child);
            }
        }
    }

    public static boolean createNewFile(File file) {
        try {
            return file.createNewFile();
        } catch (Exception e) {
            LOG.error("nop.commons.create-new-file-fail", e);
            return false;
        }
    }

    private static final int RETRY_COUNT = 10;
    private static final int SLEEP_BASETIME = 10;

    // copy from Nacos ConcurrentDiskUtil
    public static void writeTextWithLock(File file, String content, String charsetName) {
        if (!file.exists()) {
            createNewFile(file);
        }
        FileChannel channel = null;
        FileLock lock = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
            int i = 0;
            do {
                try {
                    lock = channel.tryLock();
                } catch (Exception e) {
                    ++i;
                    if (i > RETRY_COUNT) {
                        LOG.error("write {} fail;retryed time:{}", file.getName(), i);
                        throw new NopException(ERR_FILE_WRITE_CONFLICT).param(ARG_PATH, file.getAbsolutePath());
                    }
                    sleep(SLEEP_BASETIME * i);
                    LOG.warn("write {} conflict;retry time:{}", file.getName(), i);
                }
            } while (null == lock);

            ByteBuffer sendBuffer = ByteBuffer.wrap(content.getBytes(charsetName));
            while (sendBuffer.hasRemaining()) {
                channel.write(sendBuffer);
            }
            channel.truncate(content.length());
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            safeRelease(lock);
            IoHelper.safeCloseObject(channel);
            IoHelper.safeCloseObject(raf);
        }
    }

    private static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("sleep wrong", e);
        }
    }

    public static void safeRelease(FileLock lock) {
        if (lock != null) {
            try {
                lock.release();
            } catch (Exception e) {
                LOG.warn("nop.commons.file-lock.release-fail", e);
            }
        }
    }

    public static String getCanonicalPath(File file) {
        try {
            return file.getCanonicalPath().replace('\\', '/');
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 返回结果与file.toURI().toString()基本相同，但是对于目录结构，返回的url不包含尾部的/ 例如 file:/C:/test.txt
     */
    public static String getFileUrl(File file) {
        String path = getAbsolutePath(file);
        return "file:" + (path.startsWith("/") ? "" : "/") + path;
    }

    public static String getRelativeFileUrl(String path) {
        if (path.startsWith("./")) {
            File file = new File(currentDir(), path);
            return getFileUrl(file);
        }
        return getFileUrl(new File(path));
    }

    public static String getRelativePath(File base, File file) {
        String basePath = base.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        Guard.checkArgument(filePath.startsWith(basePath));
        return StringHelper.normalizePath(filePath.substring(basePath.length() + 1));
    }

    public static FileVisitResult walk(File file, Function<File, FileVisitResult> fn) {
        FileVisitResult result = fn.apply(file);
        if (result != null && result != FileVisitResult.CONTINUE)
            return result;

        File[] subFiles = file.listFiles();
        if (subFiles != null) {
            for (File subFile : subFiles) {
                result = walk(subFile, fn);

                if (result == FileVisitResult.SKIP_SIBLINGS)
                    break;

                if (result == FileVisitResult.TERMINATE)
                    return result;
            }
        }
        return FileVisitResult.SKIP_SUBTREE;
    }
}