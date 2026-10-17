/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.checkpoint.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WI13 (plan 2339): in-memory S3 fake for offline storage behavior tests. Implements
 * the SDK v2 {@link S3Client} interface — everything left on the interface default
 * (unsupported operation) throws, so only the surface {@link S3CheckpointStorage}
 * uses is overridden (the v1-era {@code AbstractAmazonS3} pattern). The bucket is
 * implicitly "present" (ConcurrentHashMap) — MinIO's create-bucket requirement is
 * covered by the gated container E2E instead.
 */
public class InMemoryAmazonS3 implements S3Client {

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
    public CreateBucketResponse createBucket(CreateBucketRequest request) {
        createBucketStore(request.bucket());
        return CreateBucketResponse.builder().build();
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        byte[] bytes;
        try {
            bytes = readStream(body.contentStreamProvider().newStream());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to read stream body", e);
        }
        bucket(request.bucket()).put(request.key(), bytes);
        return PutObjectResponse.builder().build();
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
    public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
        byte[] bytes = bucket(request.bucket()).get(request.key());
        if (bytes == null) {
            throw NoSuchKeyException.builder()
                    .message("NoSuchKey: " + request.key()).statusCode(404).build();
        }
        GetObjectResponse resp = GetObjectResponse.builder()
                .contentLength((long) bytes.length).build();
        return new ResponseInputStream<>(resp, new ByteArrayInputStream(bytes));
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest request) {
        byte[] bytes = bucket(request.bucket()).get(request.key());
        if (bytes == null) {
            throw NoSuchKeyException.builder()
                    .message("NoSuchKey: " + request.key()).statusCode(404).build();
        }
        return HeadObjectResponse.builder().contentLength((long) bytes.length).build();
    }

    @Override
    public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
        bucket(request.bucket()).remove(request.key());
        return DeleteObjectResponse.builder().build();
    }

    @Override
    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
        Map<String, byte[]> objects = bucket(request.bucket());
        List<S3Object> matched = new ArrayList<>();
        List<String> sorted = new ArrayList<>(objects.keySet());
        java.util.Collections.sort(sorted);
        for (String key : sorted) {
            if (request.prefix() == null || key.startsWith(request.prefix())) {
                matched.add(S3Object.builder()
                        .key(key)
                        .size((long) objects.get(key).length)
                        .build());
            }
        }
        ListObjectsV2Response.Builder result = ListObjectsV2Response.builder();
        int from = 0;
        String token = request.continuationToken();
        if (token != null && !token.isEmpty()) {
            from = Integer.parseInt(token);
        }
        int to = Math.min(from + pageSize, matched.size());
        result.contents(matched.subList(from, to));
        boolean truncated = to < matched.size();
        lastListTruncated = truncated;
        if (truncated) {
            result.nextContinuationToken(String.valueOf(to));
        }
        result.isTruncated(truncated);
        return result.build();
    }

    @Override
    public String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public void close() {
        // no underlying resources in the fake
    }
}
