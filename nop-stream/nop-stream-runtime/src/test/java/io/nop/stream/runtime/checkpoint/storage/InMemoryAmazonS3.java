/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.checkpoint.storage;

import com.amazonaws.services.s3.AbstractAmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WI13 (plan 2339): in-memory S3 fake for offline storage behavior tests. Extends the
 * SDK's {@link AbstractAmazonS3} (everything unimplemented throws) and overrides only
 * the surface {@link S3CheckpointStorage} uses. The bucket is implicitly "present"
 * (ConcurrentHashMap) — MinIO's create-bucket requirement is covered by the gated
 * container E2E instead.
 */
public class InMemoryAmazonS3 extends AbstractAmazonS3 {

    /** bucket → (key → bytes) */
    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private final AtomicBoolean truncatedWarned = new AtomicBoolean(false);
    /** Page size for list simulation (mirrors the single-page production ruling). */
    private int pageSize = 1000;
    volatile boolean lastListTruncated;

    public void createBucketStore(String bucket) {
        buckets.computeIfAbsent(bucket, b -> new HashMap<>());
    }

    public void clear() {
        buckets.clear();
    }

    private Map<String, byte[]> bucket(String name) {
        return buckets.computeIfAbsent(name, b -> new HashMap<>());
    }

    @Override
    public com.amazonaws.services.s3.model.Bucket createBucket(String bucket) {
        createBucketStore(bucket);
        return new com.amazonaws.services.s3.model.Bucket(bucket);
    }

    @Override
    public PutObjectResult putObject(PutObjectRequest request) {
        byte[] bytes;
        if (request.getFile() != null) {
            try {
                bytes = java.nio.file.Files.readAllBytes(request.getFile().toPath());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("failed to read file body", e);
            }
        } else {
            try {
                bytes = readStream(request.getInputStream());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("failed to read stream body", e);
            }
        }
        bucket(request.getBucketName()).put(request.getKey(), bytes);
        return new PutObjectResult();
    }

    private static byte[] readStream(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    public S3Object getObject(GetObjectRequest request) {
        byte[] bytes = bucket(request.getBucketName()).get(request.getKey());
        if (bytes == null) {
            throw new com.amazonaws.services.s3.model.AmazonS3Exception(
                    "NoSuchKey: " + request.getKey());
        }
        S3Object obj = new S3Object();
        obj.setBucketName(request.getBucketName());
        obj.setKey(request.getKey());
        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(bytes.length);
        obj.setObjectContent(new S3ObjectInputStream(
                new ByteArrayInputStream(bytes), null, false));
        obj.setObjectMetadata(meta);
        return obj;
    }

    @Override
    public void deleteObject(String bucket, String key) {
        bucket(bucket).remove(key);
    }

    @Override
    public boolean doesObjectExist(String bucket, String key) {
        return bucket(bucket).containsKey(key);
    }

    @Override
    public ListObjectsV2Result listObjectsV2(ListObjectsV2Request request) {
        Map<String, byte[]> objects = bucket(request.getBucketName());
        List<S3ObjectSummary> matched = new ArrayList<>();
        List<String> sorted = new ArrayList<>(objects.keySet());
        java.util.Collections.sort(sorted);
        for (String key : sorted) {
            if (request.getPrefix() == null || key.startsWith(request.getPrefix())) {
                S3ObjectSummary sum = new S3ObjectSummary();
                sum.setBucketName(request.getBucketName());
                sum.setKey(key);
                sum.setSize(objects.get(key).length);
                matched.add(sum);
            }
        }
        ListObjectsV2Result result = new ListObjectsV2Result();
        int from = 0;
        String token = request.getContinuationToken();
        if (token != null && !token.isEmpty()) {
            from = Integer.parseInt(token);
        }
        int to = Math.min(from + pageSize, matched.size());
        result.getObjectSummaries().addAll(matched.subList(from, to));
        boolean truncated = to < matched.size();
        lastListTruncated = truncated;
        if (truncated) {
            result.setNextContinuationToken(String.valueOf(to));
        }
        result.setTruncated(truncated);
        return result;
    }
}
