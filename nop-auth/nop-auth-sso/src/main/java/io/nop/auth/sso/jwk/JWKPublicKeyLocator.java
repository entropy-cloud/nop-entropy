/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.auth.sso.jwk;


import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.sso.SsoConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When needed, publicKeys are downloaded by sending request to realm's jwks_url
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class JWKPublicKeyLocator implements IPublicKeyLocator {

    private static final Logger log = LoggerFactory.getLogger(JWKPublicKeyLocator.class);

    private volatile Map<String, PublicKey> currentKeys = new ConcurrentHashMap<>();

    private volatile long lastRequestTime = 0;

    private SsoConfig config;

    private IHttpClient httpClient;

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setConfig(SsoConfig config) {
        this.config = config;
    }

    @Override
    public PublicKey getPublicKey(String kid) {
        int minTimeBetweenRequests = config.getMinTimeBetweenJwksRequests();
        int publicKeyCacheTtl = config.getPublicKeyCacheTtl();
        long currentTime = CoreMetrics.currentTimeMillis();

        // Check if key is in cache.
        PublicKey publicKey = lookupCachedKey(publicKeyCacheTtl, currentTime, kid);
        if (publicKey != null) {
            return publicKey;
        }

        // Check if we are allowed to send request
        synchronized (this) {
            currentTime = CoreMetrics.currentTimeMillis();
            if (currentTime > lastRequestTime + minTimeBetweenRequests * 1000L) {
                sendRequest();
                lastRequestTime = currentTime;
            } else {
                log.debug("Won't send request to realm jwks url. Last request time was {}. Current time is {}.", lastRequestTime, currentTime);
            }

            return lookupCachedKey(publicKeyCacheTtl, currentTime, kid);
        }
    }


    @Override
    public void reset() {
        synchronized (this) {
            sendRequest();
            lastRequestTime = CoreMetrics.currentTimeMillis();
            log.debug("Reset time offset to {}.", lastRequestTime);
        }
    }


    private PublicKey lookupCachedKey(int publicKeyCacheTtl, long currentTime, String kid) {
        if (lastRequestTime + publicKeyCacheTtl * 1000L > currentTime && kid != null) {
            return currentKeys.get(kid);
        } else {
            return null;
        }
    }


    private void sendRequest() {
        if (log.isTraceEnabled()) {
            log.trace("Going to send request to retrieve new set of realm public keys for client {}", config.getClientId());
        }

        try {
            JSONWebKeySet jwks = httpClient.fetch(new HttpRequest().url(config.getFullUrl(config.getJwksUrl())), null)
                    .getBodyAsBean(JSONWebKeySet.class);

            Map<String, PublicKey> publicKeys = JWKSUtils.getKeysForUse(jwks, JWK.Use.SIG);

            if (log.isDebugEnabled()) {
                log.debug("Realm public keys successfully retrieved for client {} . New kids: {}",
                        config.getClientId(), publicKeys.keySet());
            }

            this.currentKeys = new ConcurrentHashMap<>(publicKeys);
        } catch (Exception e) {
            log.error("Error when sending request to retrieve realm keys", e);
        }
    }
}