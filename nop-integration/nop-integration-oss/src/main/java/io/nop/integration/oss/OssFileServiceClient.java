package io.nop.integration.oss;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.integration.api.file.FileStatus;
import io.nop.integration.api.file.IFileServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class OssFileServiceClient implements IFileServiceClient {
    static final Logger LOG = LoggerFactory.getLogger(OssFileServiceClient.class);
    private final AmazonS3 client;
    private final OssConfig ossConfig;

    public OssFileServiceClient(AmazonS3 client, OssConfig ossConfig) {
        this.client = client;
        this.ossConfig = ossConfig;
    }

    private void makeBucket(String bucketName) {
        if (ossConfig.isAutoCreateBucket()) {
            // minio对于doesBucketExistsV2总是返回true
            try {
                boolean b = client.doesBucketExist(bucketName);
                if (b)
                    return;
            } catch (AmazonS3Exception e) {
                LOG.debug("nop.oss.check-bucket-exists-fail", e);
            }
            client.createBucket(bucketName);
        }
    }

    @Override
    public List<FileStatus> listFiles(String remotePath) {
        remotePath = normalizePath(remotePath);
        ObjectListing listing = client.listObjects(getBucketName(remotePath), remotePath);
        List<FileStatus> ret = new ArrayList<>();
        for (S3ObjectSummary summary : listing.getObjectSummaries()) {
            FileStatus status = new FileStatus();
            status.setName(StringHelper.fileFullName(summary.getKey()));
            status.setSize(summary.getSize());
            status.setLastModified(summary.getLastModified().getTime());
            ret.add(status);
        }
        return ret;
    }

    protected String getBucketName(String remotePath) {
        return ossConfig.getDefaultBucketName();
    }

    @Override
    public void deleteFile(String remotePath) {
        remotePath = normalizePath(remotePath);
        client.deleteObject(getBucketName(remotePath), remotePath);
    }

    @Override
    public FileStatus getFileStatus(String remotePath) {
        remotePath = normalizePath(remotePath);

        ObjectMetadata obj = client.getObjectMetadata(getBucketName(remotePath), remotePath);

        FileStatus status = new FileStatus();
        status.setLastModified(obj.getLastModified().getTime());
        status.setSize(obj.getContentLength());
        status.setName(StringHelper.fileFullName(remotePath));
        return status;
    }

    @Override
    public String uploadFile(String localPath, String remotePath) {
        remotePath = normalizePath(remotePath);

        String bucketName = getBucketName(remotePath);
        makeBucket(bucketName);
        client.putObject(bucketName, remotePath, new File(localPath));
        return remotePath;
    }

    @Override
    public String downloadFile(String remotePath, String localPath) {
        remotePath = normalizePath(remotePath);

        GetObjectRequest req = new GetObjectRequest(getBucketName(remotePath), remotePath);
        client.getObject(req, new File(localPath));
        return localPath;
    }

    @Override
    public String uploadResource(IResourceReference file, String remotePath) {
        remotePath = normalizePath(remotePath);
        InputStream is = file.getInputStream();
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.length());
            meta.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            String bucketName = getBucketName(remotePath);
            makeBucket(bucketName);
            client.putObject(bucketName, remotePath, is, meta);
        } finally {
            IoHelper.safeCloseObject(is);
        }

        return remotePath;
    }

    protected String normalizePath(String path){
        if(path.startsWith("/"))
            return path.substring(1);
        return path;
    }

    @Override
    public void downloadToStream(String remotePath, OutputStream out) {
        remotePath = normalizePath(remotePath);
        GetObjectRequest req = new GetObjectRequest(getBucketName(remotePath), remotePath);
        S3Object obj = client.getObject(req);
        try {
            IoHelper.copy(obj.getObjectContent(), out);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(obj);
        }
    }

    @Override
    public void close() {

    }
}
