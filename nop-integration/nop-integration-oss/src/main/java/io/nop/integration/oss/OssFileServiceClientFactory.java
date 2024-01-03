/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.oss;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.integration.api.file.IFileServiceClientFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 很多云存储都兼容s3协议: {阿里云OSS，腾讯云COS，七牛云，京东云，minio 等}
 */
public class OssFileServiceClientFactory implements IFileServiceClientFactory {
    private OssConfig ossConfig;

    private AmazonS3 client;

    public void setOssConfig(OssConfig config) {
        this.ossConfig = config;
    }

    @PostConstruct
    public void init() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                ossConfig.getEndpoint(), ossConfig.getRegion());
        AWSCredentials awsCredentials = new BasicAWSCredentials(ossConfig.getAccessKey(), //NOSONAR
                ossConfig.getSecretKey());
        AWSCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
        this.client = AmazonS3Client.builder().withEndpointConfiguration(endpointConfiguration)
                .withClientConfiguration(clientConfiguration).withCredentials(awsCredentialsProvider)
                .disableChunkedEncoding().withPathStyleAccessEnabled(ossConfig.getPathStyleAccess()).build();
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Override
    public IFileServiceClient newClient() {
        return new OssFileServiceClient(client, ossConfig);
    }
}