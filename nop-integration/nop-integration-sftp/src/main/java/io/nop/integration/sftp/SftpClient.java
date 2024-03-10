/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.integration.api.file.FileStatus;
import io.nop.integration.api.file.IFileServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import static io.nop.integration.sftp.SftpErrors.ARG_HOST;
import static io.nop.integration.sftp.SftpErrors.ARG_LOCAL_PATH;
import static io.nop.integration.sftp.SftpErrors.ARG_PORT;
import static io.nop.integration.sftp.SftpErrors.ARG_REMOTE_PATH;
import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_CONNECT_FAIL;
import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_DELETE_FILE_FAIL;
import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_DOWNLOAD_FILE_FAIL;
import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_LIST_FILE_FAIL;
import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_UPLOAD_FILE_FAIL;

public class SftpClient implements IFileServiceClient {
    static final Logger LOG = LoggerFactory.getLogger(SftpClient.class);

    private final SftpConfig config;
    private JSch jsch;
    private ChannelSftp channel;
    private Session session;

    public SftpClient(SftpConfig config) {
        this.config = config;
        this.connect();
    }

    protected void connect() {
        this.jsch = new JSch();
        try {
            if (config.getKeyPath() != null) {
                jsch.addIdentity(config.getKeyPath(), config.getPassphrase());
            }
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            //disable known hosts checking
            // jsch.setKnownHosts("path to known hosts file");
            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);

            // 没有设置密钥时才会使用密码
            if (config.getKeyPath() == null)
                session.setPassword(this.config.getPassword());

            session.connect();
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_CONNECT_FAIL, e)
                    .param(ARG_HOST, config.getHost())
                    .param(ARG_PORT, config.getPort());
        }
    }

    @Override
    public List<FileStatus> listFiles(String remoteDir) {
        try {
            Vector<ChannelSftp.LsEntry> files = channel.ls(remoteDir);
            List<FileStatus> ret = new ArrayList<>(files.size());

            for (ChannelSftp.LsEntry file : files) {
                String name = file.getFilename();
                SftpATTRS attrs = file.getAttrs();

                ret.add(newFileStatus(name, attrs));
            }
            return ret;
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_LIST_FILE_FAIL, e)
                    .param(ARG_REMOTE_PATH, remoteDir);
        }
    }

    private FileStatus newFileStatus(String name, SftpATTRS attrs) {
        String permissions = attrs.getPermissionsString();
        long size = attrs.getSize();

        return new FileStatus(name, size, attrs.getMTime() * 1000L, permissions);
    }

    @Override
    public FileStatus getFileStatus(String remotePath) {
        try {
            SftpATTRS attrs = channel.lstat(remotePath);
            return newFileStatus(getFileName(remotePath), attrs);
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_LIST_FILE_FAIL, e)
                    .param(ARG_REMOTE_PATH, remotePath);
        }
    }

    private String getFileName(String path) {
        int pos = path.lastIndexOf('/');
        if (pos < 0)
            return path;
        return path.substring(pos + 1);
    }

    @Override
    public String uploadFile(String localPath, String remotePath) {
        try {
            remotePath = channel.realpath(remotePath);
            channel.put(localPath, remotePath);
            return remotePath;
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_UPLOAD_FILE_FAIL, e)
                    .param(ARG_LOCAL_PATH, localPath)
                    .param(ARG_REMOTE_PATH, remotePath);
        }
    }

    @Override
    public String uploadResource(IResourceReference resource, String remotePath) {
        InputStream in = null;
        try {
            remotePath = channel.realpath(remotePath);
            in = resource.getInputStream();
            channel.put(in, remotePath);
            return remotePath;
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_UPLOAD_FILE_FAIL, e)
                    .param(ARG_LOCAL_PATH, resource.getPath())
                    .param(ARG_REMOTE_PATH, remotePath);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                LOG.debug("close fail", e);
            }
        }
    }

    @Override
    public String downloadFile(String remotePath, String localPath) {
        try {
            channel.get(remotePath, localPath);
            return localPath;
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_DOWNLOAD_FILE_FAIL, e)
                    .param(ARG_LOCAL_PATH, localPath)
                    .param(ARG_REMOTE_PATH, remotePath);
        }
    }

    @Override
    public void downloadToStream(String remotePath, OutputStream out) {
        try {
            channel.get(remotePath, out);
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_DOWNLOAD_FILE_FAIL, e)
                    .param(ARG_REMOTE_PATH, remotePath);
        }
    }

    public boolean deleteFile(String remotePath) {
        LOG.info("nop.sftp.delete:remotePath={}", remotePath);
        try {
            channel.rm(remotePath);
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_DELETE_FILE_FAIL, e)
                    .param(ARG_REMOTE_PATH, remotePath);
        }
        return true;
    }

    @Override
    public InputStream getInputStream(String remotePath) {
        LOG.info("nop.sftp.delete:remotePath={}", remotePath);
        try {
            return channel.get(remotePath);
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_DELETE_FILE_FAIL, e)
                    .param(ARG_REMOTE_PATH, remotePath);
        }
    }

    /**
     * Disconnect from remote
     */
    public void close() {
        try {
            if (channel != null) {
                channel.exit();
            }
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}