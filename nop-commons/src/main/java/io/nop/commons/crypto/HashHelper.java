/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.crypto;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import jakarta.annotation.Nonnull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashHelper {
    // ThreadLocal重用MessageDigest
    private static ThreadLocal<MessageDigest> createThreadLocalMessageDigest(final String digest) {
        return new ThreadLocal<>() {
            @Override
            protected MessageDigest initialValue() {
                try {
                    return MessageDigest.getInstance(digest);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(
                            "unexpected exception creating MessageDigest instance for [" + digest + "]", e);
                }
            }
        };
    }

//    private static ThreadLocal<Mac> createThreadLocalMac(final String algorithm) {
//        return new ThreadLocal<>() {
//            @Override
//            protected Mac initialValue() {
//                try {
//                    return Mac.getInstance(algorithm);
//                } catch (NoSuchAlgorithmException e) {
//                    throw new RuntimeException(
//                            "unexpected exception creating MessageDigest instance for [" + algorithm + "]", e);
//                }
//            }
//        };
//    }

    private static final ThreadLocal<MessageDigest> MD5_DIGEST = createThreadLocalMessageDigest("MD5");
    private static final ThreadLocal<MessageDigest> SHA_1_DIGEST = createThreadLocalMessageDigest("SHA-1");
    private static final ThreadLocal<MessageDigest> SHA_256_DIGEST = createThreadLocalMessageDigest("SHA-256");
    private static final ThreadLocal<MessageDigest> SHA_512_DIGEST = createThreadLocalMessageDigest("SHA-512");

   // private static final ThreadLocal<Mac> HMAC_SHA_256 = createThreadLocalMac("HmacSHA256");

    public static void clear(){
        MD5_DIGEST.remove();
        SHA_1_DIGEST.remove();
        SHA_256_DIGEST.remove();
        SHA_512_DIGEST.remove();
    }

    /**
     * 对输入字符串进行sha1散列，带salt达到更高的安全性.
     */
    public static byte[] md5(@Nonnull byte[] input) {
        return digest(input, get(MD5_DIGEST), null, 1);
    }

    ////////////////// SHA1 ///////////////////
    public static byte[] sha1(@Nonnull byte[] input) {
        return digest(input, get(SHA_1_DIGEST), null, 1);
    }

    /**
     * 对输入字符串进行sha1散列，带salt达到更高的安全性.
     */
    public static byte[] sha1(@Nonnull byte[] input, byte[] salt) {
        return digest(input, get(SHA_1_DIGEST), salt, 1);
    }

    public static byte[] sha256(@Nonnull byte[] input, byte[] salt) {
        return digest(input, get(SHA_256_DIGEST), salt, 1);
    }

    public static byte[] hmacSha256(@Nonnull byte[] input, byte[] key) {
        return hmac("HmacSHA256", input, key);
    }

    public static byte[] sha512(@Nonnull byte[] input, byte[] salt) {
        return digest(input, get(SHA_512_DIGEST), salt, 1);
    }

    /**
     * 对输入字符串进行sha1散列，带salt而且迭代达到更高更高的安全性.
     *
     * @see #generateSalt(int)
     */
    public static byte[] sha1(@Nonnull byte[] input, byte[] salt, int iterations) {
        return digest(input, get(SHA_1_DIGEST), salt, iterations);
    }

    private static MessageDigest get(ThreadLocal<MessageDigest> messageDigest) {
        MessageDigest instance = messageDigest.get();
        instance.reset();
        return instance;
    }

    /**
     * 对字符串进行散列, 支持md5与sha1算法.
     */
    private static byte[] digest(@Nonnull byte[] input, MessageDigest digest, byte[] salt, int iterations) {
        // 带盐
        if (salt != null) {
            digest.update(salt);
        }

        // 第一次散列
        byte[] result = digest.digest(input);

        // 如果迭代次数>1，进一步迭代散列
        for (int i = 1; i < iterations; i++) {
            digest.reset();
            result = digest.digest(result);
        }

        return result;
    }

    /**
     * 用SecureRandom生成随机的byte[]作为salt.
     *
     * @param numBytes salt数组的大小
     */
    public static byte[] generateSalt(int numBytes) {
        Guard.checkArgument(numBytes > 0, "numBytes argument must be a positive integer (1 or larger)");

        byte[] bytes = new byte[numBytes];
        MathHelper.secureRandom().nextBytes(bytes);
        return bytes;
    }

    public static byte[] hmac(String algorithm, byte[] bytes, byte[] key) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static int murmur3_32(String str) {
        return Hashing.murmur3_32().hashString(str, StringHelper.CHARSET_UTF8).asInt();
    }

    public static IHash64Function<String> murmur3_64_string() {
        HashFunction h = Hashing.murmur3_128();
        return str -> {
            return h.hashString(str, StringHelper.CHARSET_UTF8).asLong();
        };
    }
}
