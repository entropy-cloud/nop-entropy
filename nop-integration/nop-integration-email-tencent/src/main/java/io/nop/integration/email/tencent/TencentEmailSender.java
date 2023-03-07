/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.email.tencent;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.Simple;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TencentEmailSender implements IEmailSender {
    public static final String ENDPOINT = "ses.tencentcloudapi.com";

    static final Logger LOG = LoggerFactory.getLogger(TencentEmailSender.class);

    private String region;

    private String secretId;

    private String secretKey;

    private SesClient client;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    protected SesClient newClient() {
        // instantiate an authentication object
        Credential cred = new Credential(secretId, secretKey);

        // instantiate a http option
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(ENDPOINT);

        // Instantiate a client option
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        // instantiate the client object of the requested product
        return new SesClient(cred, region, clientProfile);
    }

    @Override
    public void sendEmail(EmailMessage mail) {
        SesClient client = newClient();
        try {
            SendEmailRequest req = new SendEmailRequest();

            req.setSubject(mail.getSubject());
            req.setFromEmailAddress(mail.getFullFrom());
            req.setDestination(mail.getTo().toArray(new String[0]));
            if (mail.getReply() != null)
                req.setReplyToAddresses(mail.getReply());
            Simple simple = new Simple();
            if (mail.isHtml()) {
                simple.setHtml(mail.getText());
            } else {
                simple.setText(mail.getText());
            }

            req.setSimple(simple);
            client.SendEmail(req);
        } catch (TencentCloudSDKException e) {
            String ignoreInfo = "EmailAddressIsNULL";
            if (e.getErrorCode().contains(ignoreInfo)) {
                return;
            } else if ("FailedOperation.FrequencyLimit".equals(e.getErrorCode())) {
                LOG.warn("nop.send-email-exceed-limit", e);
                return;
            }
            LOG.error("nop.err.send-email-fail", e);
        }
    }
}
