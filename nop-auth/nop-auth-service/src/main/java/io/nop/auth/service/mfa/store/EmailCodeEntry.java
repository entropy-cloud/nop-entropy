/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * Redis 端存储的邮件验证码条目（设计 §5.3.3，W15-impl；{@code SmsCodeEntry} 平行类）。
 * expireAtMillis 作为 Local/Redis 一致的过期判定来源；Redis 自身的 putExAsync TTL 是兜底。
 * <p>
 * {@code @DataBean}（W12 教训）：平台 JSON 序列化缺省仅允许 DataBean
 * （{@code nop.core.json.serialize-only-data-bean=true}）——缺该标记时经真实
 * PrefixTextCodec 的 Redis 写路径会抛 only-data-bean-is-serializable。
 */
@DataBean
public class EmailCodeEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private long expireAtMillis;

    public EmailCodeEntry() {
    }

    public EmailCodeEntry(String code, long expireAtMillis) {
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
        if (!(o instanceof EmailCodeEntry))
            return false;
        EmailCodeEntry that = (EmailCodeEntry) o;
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
