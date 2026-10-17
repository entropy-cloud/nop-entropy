/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.oss;

import io.nop.api.core.beans.file.FileStatusBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.integration.api.file.IFileServiceClient;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OssFileServiceClient implements IFileServiceClient {
    static final Logger LOG = LoggerFactory.getLogger(OssFileServiceClient.class);
    private final S3Client client;
    private final OssConfig ossConfig;

    public OssFileServiceClient(S3Client client, OssConfig ossConfig) {
        this.client = client;
        this.ossConfig = ossConfig;
    }

    private void makeBucket(String bucketName) {
        if (ossConfig.isAutoCreateBucket()) {
            // minio对bucket存在性检查的异常路径行为不一：head 失败（含 404/403 掩码）一律
            // 记 debug 后尝试创建——保持 v1 doesBucketExist 异常吞并后 createBucket 的语义
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                return;
            } catch (S3Exception e) {
                LOG.debug("nop.oss.check-bucket-exists-fail", e);
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    @Override
    public List<FileStatusBean> listFiles(String remotePath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);
        ListObjectsV2Response listing = client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName).prefix(remotePath).build());
        List<FileStatusBean> ret = new ArrayList<>();
        for (S3Object summary : listing.contents()) {
            FileStatusBean status = new FileStatusBean();
            status.setName(StringHelper.fileFullName(summary.key()));
            status.setSize(summary.size());
            status.setLastModified(summary.lastModified().toEpochMilli());
            ret.add(status);
        }
        return ret;
    }

    protected String getBucketName(String remotePath) {
        if (remotePath.startsWith(BUCKET_PREFIX)) {
            int pos = remotePath.indexOf('/', 1);
            return remotePath.substring(BUCKET_PREFIX.length(), pos);
        }
        return ossConfig.getDefaultBucketName();
    }

    @Override
    public boolean deleteFile(String remotePath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(remotePath).build());
        return true;
    }

    @Override
    public FileStatusBean getFileStatus(String remotePath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);

        HeadObjectResponse obj = client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName).key(remotePath).build());

        FileStatusBean status = new FileStatusBean();
        status.setLastModified(obj.lastModified().toEpochMilli());
        status.setSize(obj.contentLength());
        status.setName(StringHelper.fileFullName(remotePath));
        status.setExternalPath(getExternalPath(remotePath, obj));
        return status;
    }

    protected String getExternalPath(String remotePath, HeadObjectResponse obj) {
        if (ossConfig.isReturnRemotePathAsExternalPath())
            return remotePath;
        return null;
    }

    @Override
    public String uploadFile(String localPath, String remotePath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);

        makeBucket(bucketName);
        client.putObject(PutObjectRequest.builder().bucket(bucketName).key(remotePath).build(),
                RequestBody.fromFile(Path.of(localPath)));
        return remotePath;
    }

    @Override
    public String downloadFile(String remotePath, String localPath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);

        GetObjectRequest req = GetObjectRequest.builder().bucket(bucketName).key(remotePath).build();
        client.getObject(req, ResponseTransformer.toFile(Path.of(localPath)));
        return localPath;
    }

    @Override
    public String uploadResource(IResourceReference file, String remotePath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);
        InputStream is = file.getInputStream();
        try {
            makeBucket(bucketName);
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName).key(remotePath)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .contentLength(file.length())
                            .build(),
                    RequestBody.fromInputStream(is, file.length()));
        } finally {
            IoHelper.safeCloseObject(is);
        }

        return remotePath;
    }

    protected String normalizePath(String path) {
        if (path.startsWith("/"))
            return path.substring(1);
        return path;
    }

    @Override
    public void downloadToStream(String remotePath, OutputStream out) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);
        GetObjectRequest req = GetObjectRequest.builder().bucket(bucketName).key(remotePath).build();
        // ResponseInputStream 持有 HTTP 流——拷贝完成后必须关闭（对应 v1 关闭 S3Object）
        try (ResponseInputStream<GetObjectResponse> obj = client.getObject(req)) {
            IoHelper.copy(obj, out);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public InputStream getInputStream(String remotePath) {
        String bucketName = getBucketName(remotePath);
        remotePath = normalizePath(remotePath);
        GetObjectRequest req = GetObjectRequest.builder().bucket(bucketName).key(remotePath).build();
        return client.getObject(req);
    }

    @Override
    public void close() {

    }
}
