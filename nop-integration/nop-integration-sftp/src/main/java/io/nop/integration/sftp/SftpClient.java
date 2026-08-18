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
import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.api.file.IFileServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
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

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl-ext 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_SFTP_SSH = "sftp-ssh";

    private final SftpConfig config;

    /**
     * 凭证消费 SPI（可选装配；{@code SftpClientFactory} 注入，直构路径为 null）：部署不含
     * nop-credential 时为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），
     * 静态路径不受影响。
     */
    private final ICredentialProvider credentialProvider;

    private JSch jsch;
    private ChannelSftp channel;
    private Session session;

    public SftpClient(SftpConfig config) {
        this(config, null);
    }

    SftpClient(SftpConfig config, ICredentialProvider credentialProvider) {
        this.config = config;
        this.credentialProvider = credentialProvider;
        this.connect();
    }

    /**
     * 单次操作的凭证组（逐次操作期解析——`SftpClientFactory.newClient` 每次新建，凭证轮换/禁用
     * 下次操作即生效）。三字段均可空（公钥无口令场景合法）。
     */
    static final class ResolvedCredential {
        final String username;
        final String password;
        final String passphrase;

        ResolvedCredential(String username, String password, String passphrase) {
            this.username = username;
            this.password = password;
            this.passphrase = passphrase;
        }
    }

    /**
     * 优先级链判定（共享解析支持单点语义）：config.credentialId 空/空白 → SftpConfig 静态
     * username/password/passphrase；非空 → 解析 {@code sftp-ssh} 字段集整组覆盖（同名静态值忽略），
     * 解析失败 fail-closed 抛 {@code NopException}（不回退静态值）。包可见以便单元测试三态断言。
     */
    ResolvedCredential resolveCredential() {
        String credentialId = config != null ? config.getCredentialId() : null;
        if (!CredentialResolutionSupport.isConfigured(credentialId)) {
            return new ResolvedCredential(config.getUsername(), config.getPassword(), config.getPassphrase());
        }
        CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                Set.of(CREDENTIAL_TYPE_SFTP_SSH));
        return new ResolvedCredential(
                CredentialResolutionSupport.optionalString(data, "username"),
                CredentialResolutionSupport.optionalString(data, "password"),
                CredentialResolutionSupport.optionalString(data, "passphrase"));
    }

    protected void connect() {
        // 解析在 try 之前执行：fail-closed 的 NopException 独立失败，保留 IntegrationErrors 码语义
        // （不得被下方 ERR_SFTP_CONNECT_FAIL 包装吞没）——W16-impl-ext plan Phase 3 落点约束。
        ResolvedCredential credential = resolveCredential();
        this.jsch = newJsch();
        try {
            openConnection(credential);
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_CONNECT_FAIL, e)
                    .param(ARG_HOST, config.getHost())
                    .param(ARG_PORT, config.getPort());
        }
    }

    /**
     * jsch 构造点（protected seam 供测试替换）。
     */
    protected JSch newJsch() {
        return new JSch();
    }

    /**
     * jsch 凭证消费点 seam（W16-impl-ext）：{@code addIdentity}（keyPath + passphrase）/
     * {@code getSession}（username）/{@code setPassword}（password）三消费点集中于此，credentialId
     * 路径下使用解析后的凭证组（同首批 {@code createSender} 先例）。
     */
    protected void openConnection(ResolvedCredential credential) throws Exception {
        String keyPath = config.getKeyPath();
        if (keyPath != null) {
            jsch.addIdentity(keyPath, credential.passphrase);
        }
        session = jsch.getSession(credential.username, config.getHost(), config.getPort());
        //disable known hosts checking
        // jsch.setKnownHosts("path to known hosts file");
        Properties props = new Properties();
        props.put("StrictHostKeyChecking", "no");
        session.setConfig(props);

        // 没有设置密钥时才会使用密码
        if (keyPath == null)
            session.setPassword(credential.password);

        session.connect();
        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
    }

    @Override
    public List<FileStatusBean> listFiles(String remoteDir) {
        try {
            Vector<ChannelSftp.LsEntry> files = channel.ls(remoteDir);
            List<FileStatusBean> ret = new ArrayList<>(files.size());

            for (ChannelSftp.LsEntry file : files) {
                String name = file.getFilename();
                SftpATTRS attrs = file.getAttrs();
                String remotePath = appendPath(remoteDir, name);
                ret.add(newFileStatus(remotePath, name, attrs));
            }
            return ret;
        } catch (Exception e) {
            throw new NopException(ERR_SFTP_LIST_FILE_FAIL, e)
                    .param(ARG_REMOTE_PATH, remoteDir);
        }
    }

    protected String appendPath(String remoteDir, String name) {
        if (remoteDir.endsWith("/")) {
            return remoteDir + name;
        } else {
            return remoteDir + "/" + name;
        }
    }

    private FileStatusBean newFileStatus(String remotePath, String name, SftpATTRS attrs) {
        String permissions = attrs.getPermissionsString();
        long size = attrs.getSize();

        FileStatusBean status = new FileStatusBean(name, size, attrs.getMTime() * 1000L, permissions);
        status.setExternalPath(getExternalPath(remotePath, attrs));
        return status;
    }

    protected String getExternalPath(String remotePath, SftpATTRS attrs) {
        return null;
    }

    @Override
    public FileStatusBean getFileStatus(String remotePath) {
        try {
            SftpATTRS attrs = channel.lstat(remotePath);
            return newFileStatus(remotePath, getFileName(remotePath), attrs);
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