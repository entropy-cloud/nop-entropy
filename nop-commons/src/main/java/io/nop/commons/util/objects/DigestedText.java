/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util.objects;

import io.nop.api.core.convert.IByteArrayView;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;

/**
 * 封装一串文本以及它的sha1摘要。摘要算法与RedisScript的摘要算法相同，因此可以用于存储Redis的lua脚本
 */
public class DigestedText implements Serializable, IByteArrayView {

    private static final long serialVersionUID = -6571961961035795895L;

    private final String text;
    private final byte[] sha;

    private final byte[] bytes;

    private final String digestString;

    public DigestedText(String text) {
        this.text = text;
        this.bytes = text.getBytes(StringHelper.CHARSET_UTF8);
        this.sha = calcSha(bytes);
        this.digestString = StringHelper.bytesToHex(sha, true);
    }

    private byte[] calcSha(byte[] bytes) {
        return HashHelper.sha1(bytes);
    }

    @Override
    public byte[] toByteArray() {
        return bytes;
    }

    public String getDigestString() {
        return digestString;
    }

    public String getText() {
        return text;
    }

    public byte[] getSha() {
        return sha;
    }

    public int hashCode() {
        return text.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof DigestedText))
            return false;
        DigestedText other = (DigestedText) o;
        return other.text.equals(text);
    }
}
