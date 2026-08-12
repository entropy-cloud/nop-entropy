/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import java.io.Serializable;

/**
 * Redis 端存储的短信验证码条目（nop-nosql PrefixTextCodec 要求可 JSON 序列化 POJO）。
 * expireAtMillis 作为 Local/Redis 一致的过期判定来源；Redis 自身的 putExAsync TTL 是兜底。
 */
public class SmsCodeEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private long expireAtMillis;

    public SmsCodeEntry() {
    }

    public SmsCodeEntry(String code, long expireAtMillis) {
        this.code = code;
        this.expireAtMillis = expireAtMillis;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public long getExpireAtMillis() {
        return expireAtMillis;
    }

    public void setExpireAtMillis(long expireAtMillis) {
        this.expireAtMillis = expireAtMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SmsCodeEntry))
            return false;
        SmsCodeEntry that = (SmsCodeEntry) o;
        return expireAtMillis == that.expireAtMillis
                && (code == null ? that.code == null : code.equals(that.code));
    }

    @Override
    public int hashCode() {
        int h = code == null ? 0 : code.hashCode();
        h = 31 * h + (int) (expireAtMillis ^ (expireAtMillis >>> 32));
        return h;
    }
}
