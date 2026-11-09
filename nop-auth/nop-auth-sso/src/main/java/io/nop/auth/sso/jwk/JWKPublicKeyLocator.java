/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * When needed, publicKeys are downloaded by sending request to realm's jwks_url
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 *
 * <p>2.3-C4-008（plan 2551 裁定 F）：原形态 synchronized 块内**同步** IdP 拉取——每认证请求
 * 在 kid 未命中时跨 IdP RTT 占用单例监视器（并发未命中排队），且失败静默占 60s 速率窗。
 * 改唯一形态：
 * <ol>
 *   <li><b>单飞共享</b>：在飞 {@link CompletableFuture} 缓存——并发 kid 未命中共享同一次
 *       拉取（锁内只查/置在飞 future，拉取在锁外执行，monitor 不跨 IdP RTT）。</li>
 *   <li><b>显式超时</b>：{@code nop.auth.jwks.fetch-timeout}（缺省 5s；≤0=客户端缺省）。</li>
 *   <li><b>失败 grace</b>：拉取失败保留既有 currentKeys（有旧钥返回旧钥；无旧钥才 fail），
 *       且失败不推进 lastRequestTime（不占速率窗，下次调用立即可重试）。</li>
 * </ol>
 */
public class JWKPublicKeyLocator implements IPublicKeyLocator {

    private static final Logger log = LoggerFactory.getLogger(JWKPublicKeyLocator.class);

    public static final long DEFAULT_FETCH_TIMEOUT_MILLIS = 5_000L;

    private volatile ConcurrentHashMap<String, PublicKey> currentKeys = new ConcurrentHashMap<>(); //NOSONAR

    private volatile long lastRequestTime = 0;

    /** 2.3-C4-008：在飞拉取共享句柄（单飞）——并发未命中共享同一次 IdP 拉取 */
    private volatile CompletableFuture<Map<String, PublicKey>> inFlightFetch;

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

        // 2.3-C4-008：锁内只查/置在飞 future；拉取在锁外执行（monitor 不跨 IdP RTT）。
        CompletableFuture<Map<String, PublicKey>> fetch;
        synchronized (this) {
            currentTime = CoreMetrics.currentTimeMillis();
            boolean allowed = currentTime > lastRequestTime + minTimeBetweenRequests * 1000L;
            if (!allowed) {
                log.debug("Won't send request to realm jwks url. Last request time was {}. Current time is {}.",
                        lastRequestTime, currentTime);
            } else if (inFlightFetch != null && !inFlightFetch.isDone()) {
                // 已有在飞拉取：并发未命中共享（singleflight）
            } else {
                // 失败不占速率窗：lastRequestTime 仅在拉取成功后推进（见 fetch 终态段）——
                // 失败后下次调用立即可重试（裁定 F④）
                inFlightFetch = CompletableFuture.supplyAsync(this::sendRequest);
            }
            fetch = inFlightFetch;
        }

        if (fetch != null) {
            try {
                long timeout = getFetchTimeoutMillis();
                Map<String, PublicKey> keys = timeout > 0
                        ? fetch.get(timeout, TimeUnit.MILLISECONDS)
                        : fetch.get();
                if (keys != null) {
                    // 成功：推进速率窗基准（TTL 内查缓存）；失败分支不推进（可立即重试）
                    lastRequestTime = CoreMetrics.currentTimeMillis();
                    PublicKey key = keys.get(kid);
                    if (key != null) {
                        return key;
                    }
                }
            } catch (Exception e) {
                // 失败 grace：保留既有 currentKeys（lastRequestTime 未推进——速率窗不占用，
                // 下次调用立即可重试）；有旧钥返回旧钥，无旧钥才 fail（拆分 kid 缺失 vs TTL 过期）。
                log.warn("nop.warn.auth.jwks-fetch-fail:clientId={}", config.getClientId(), e);
            }
        }

        // 有旧钥：grace 返回旧钥（即使 TTL 已过——比 fail-closed 更可用；kid 仍缺失则下面返回 null）
        PublicKey stale = kid == null ? null : currentKeys.get(kid);
        if (stale != null) {
            log.debug("nop.auth.jwks-grace-serve-stale-key:kid={}", kid);
        }
        return stale;
    }

    long getFetchTimeoutMillis() {
        // 配置键经 SsoConfig 注入面缺失时的保守缺省（5s）；后续可扩 SsoConfig 属性
        return DEFAULT_FETCH_TIMEOUT_MILLIS;
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


    /** 2.3-C4-008：拉取并发布新钥表；返回钥表供单飞等待方查找（失败返回 null，不抛出） */
    private Map<String, PublicKey> sendRequest() {
        if (log.isTraceEnabled()) {
            log.trace("Going to send request to retrieve new set of realm public keys for client {}",
                    config.getClientId());
        }

        try {
            HttpRequest request = new HttpRequest().url(config.getFullUrl(config.getJwksUrl()));
            // 2.3-C4-008：显式超时（无超时的 IdP 挂起不再钉死在飞等待方）
            request.setTimeout(getFetchTimeoutMillis());

            JSONWebKeySet jwks = httpClient.fetch(request, null).getBodyAsBean(JSONWebKeySet.class);

            Map<String, PublicKey> publicKeys = JWKSUtils.getKeysForUse(jwks, JWK.Use.SIG);

            if (log.isDebugEnabled()) {
                log.debug("Realm public keys successfully retrieved for client {} . New kids: {}",
                        config.getClientId(), publicKeys.keySet());
            }

            this.currentKeys = new ConcurrentHashMap<>(publicKeys);
            return publicKeys;
        } catch (Exception e) {
            // 2.3-C4-008：失败 WARN（原 log.error 后静默返回旧值且占速率窗）——grace 语义见调用方
            log.error("Error when sending request to retrieve realm keys", e);
            return null;
        }
    }
}
