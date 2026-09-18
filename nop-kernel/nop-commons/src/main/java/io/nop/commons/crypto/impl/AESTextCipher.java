/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.crypto.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.Bytes;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.crypto.IStreamCipher;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_ENC_KEY;
import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_IV;

public class AESTextCipher implements ITextCipher, IStreamCipher {
    private static final Logger LOG = LoggerFactory.getLogger(AESTextCipher.class);

    public static final int GCM_TAG_LENGTH = 16;
    public static final int GCM_IV_LENGTH = 12; // 其他模式的IV长度都是16

    public static final int AES_IV_LENGTH = 16;

    /**
     * 自描述版本化密文格式的版本标记。明文输出形如 {@code v1:<base64(iv||ciphertext+tag>}。
     * 选用 {@code v1:} 前缀是因为 legacy 输出（base64 或 hex）的字母表都不包含冒号，
     * 因此 legacy 密文永远不可能以该标记开头，检测规则无歧义。
     */
    public static final String V1_MARKER = "v1:";

    /**
     * v1 格式使用 PBKDF2WithHmacSHA256 派生密钥。salt 取自 {@code saltKey}（为空时使用固定盐）。
     */
    public static final int V1_KDF_ITERATIONS = 65536;
    public static final int V1_KEY_BITS = 256;

    static final byte[] DEFAULT_V1_SALT = HashHelper.sha256(
            "nop-entropy-aes-v1-default-salt".getBytes(StandardCharsets.UTF_8), new byte[0]);
    static final byte[] DEFAULT_IV = StringHelper
            .hexToBytes(hashWithDefault(CFG_CRYPT_DEFAULT_IV.get(), "*(<K:00a9mf8ia7Nn3^y34%FER{3/"));

    static final byte[] DEFAULT_GCM_IV = Bytes.head(DEFAULT_IV, GCM_IV_LENGTH);

    static String hashWithDefault(String str, String defaultValue) {
        if (StringHelper.isEmpty(str))
            str = defaultValue;
        return StringHelper.md5Hash(str);
    }

    private boolean base64Encode = true;

    private String encKey = CFG_CRYPT_DEFAULT_ENC_KEY.get();

    private String saltKey = "";

    private final String cipherName;

    /**
     * legacy 路径 AES 密钥的缓存。{@link SecretKeySpec} 不可变，volatile保证setEncKey/setSaltKey
     * 清空缓存后其他线程能立即感知（对齐 {@link #v1SecretKey} 的处理）。
     */
    private volatile SecretKeySpec secretKey;
    private byte[] iv;

    /**
     * 是否启用自描述版本化密文格式（默认 true）。v1 格式：每次加密使用随机 IV，
     * PBKDF2 派生密钥，输出带 {@link #V1_MARKER} 前缀。设置为 false 时回退到 legacy
     * 行为（静态 IV + MD5 派生密钥），仅用于显式 opt-out 场景。
     */
    private boolean versionedFormat = true;

    /**
     * v1 格式 PBKDF2 派生密钥的缓存。{@link SecretKeySpec} 本身不可变，volatile 保证
     * 多线程可见性；重新计算是幂等的，因此并发首派生的竞态不会破坏正确性。
     */
    private volatile SecretKeySpec v1SecretKey;

    /**
     * 空密钥一次性告警标志。volatile 保证多线程可见；并发首派生竞态下最多重复告警
     * 一次（幂等噪音，可接受）。仅在真正执行密钥派生（缓存未命中）时触发，
     * 直接经 {@link #secretKey(SecretKeySpec)} 注入密钥的调用方不会被误报。
     */
    private volatile boolean emptyKeyWarned;

    public AESTextCipher() {
        this("AES/GCM/NoPadding");
    }

    public AESTextCipher(String cipherName) {
        this.cipherName = cipherName;
        if (isGCM()) {
            this.iv = DEFAULT_GCM_IV;
        } else {
            this.iv = DEFAULT_IV;
        }
    }

    /**
     * 是否将IV拼接在加密结果的前面
     */
    private boolean concatIv;

    public int getIvLength() {
        return isGCM() ? GCM_IV_LENGTH : AES_IV_LENGTH;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public void setConcatIv(boolean concatIv) {
        this.concatIv = concatIv;
    }

    public AESTextCipher concatIv(boolean concatIv) {
        this.concatIv = concatIv;
        return this;
    }

    public AESTextCipher iv(byte[] iv) {
        this.iv = iv;
        return this;
    }

    public boolean isGCM() {
        return cipherName.indexOf("/GCM/") > 0;
    }

    public AESTextCipher generateIv() {
        this.iv = new byte[isGCM() ? 12 : 16];
        MathHelper.secureRandom().nextBytes(iv);
        return this;
    }

    public void setSaltKey(String saltKey) {
        this.saltKey = saltKey;
        this.secretKey = null;
        this.v1SecretKey = null;
    }

    public void setEncKey(String encKey) {
        this.encKey = encKey;
        this.secretKey = null;
        this.v1SecretKey = null;
    }

    public boolean isVersionedFormat() {
        return versionedFormat;
    }

    public void setVersionedFormat(boolean versionedFormat) {
        this.versionedFormat = versionedFormat;
    }

    public AESTextCipher versionedFormat(boolean versionedFormat) {
        this.versionedFormat = versionedFormat;
        return this;
    }

    public String getCipherName() {
        return cipherName;
    }

    public AESTextCipher saltKey(String saltKey) {
        this.setSaltKey(saltKey);
        return this;
    }

    public AESTextCipher encKey(String encKey) {
        this.setEncKey(encKey);
        return this;
    }

    public AESTextCipher secretKey(SecretKeySpec key) {
        this.secretKey = key;
        return this;
    }

    public void setBase64Encode(boolean base64Encode) {
        this.base64Encode = base64Encode;
    }

    /**
     * encKey 为空时派生出的密钥可被任何持有源码的人公开推导（F-C3-1）。
     * 不阻断（兼容开发/测试模式），但每个实例首次派生时告警一次。
     */
    void warnEmptyKeyOnce() {
        if (!emptyKeyWarned) {
            LOG.warn("nop.crypt.empty-enc-key:AES cipher is deriving keys with an empty enc-key;"
                    + " the derived key is publicly derivable and offers no confidentiality."
                    + " Configure nop.config.encrypt-key or nop.crypt.default-enc-key for production use");
            emptyKeyWarned = true;
        }
    }

    /** 测试观测用：本实例是否已因空 encKey 派生密钥而告警。 */
    boolean wasEmptyKeyWarned() {
        return emptyKeyWarned;
    }

    SecretKeySpec buildSecretKey() {
        if (secretKey != null) {
            return secretKey;
        }

        if (StringHelper.isEmpty(encKey))
            warnEmptyKeyOnce();

        try {
            byte[] bytes = ((encKey + saltKey)).getBytes(StringHelper.CHARSET_UTF8);
            byte[] encoded = HashHelper.md5(bytes);

            SecretKeySpec key = new SecretKeySpec(encoded, "AES");
            this.secretKey = key;
            return key;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * v1 格式使用 PBKDF2WithHmacSHA256 从 {@code encKey}/{@code saltKey} 派生 AES 密钥。
     * KDF 按版本标记分派：legacy 走 {@link #buildSecretKey()}（MD5），v1 走本方法。
     */
    SecretKeySpec buildV1SecretKey() {
        SecretKeySpec cached = this.v1SecretKey;
        if (cached != null) {
            return cached;
        }
        if (StringHelper.isEmpty(encKey))
            warnEmptyKeyOnce();
        try {
            byte[] salt = (saltKey == null || saltKey.isEmpty()) ? DEFAULT_V1_SALT
                    : saltKey.getBytes(StringHelper.CHARSET_UTF8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(encKey.toCharArray(), salt, V1_KDF_ITERATIONS, V1_KEY_BITS);
            try {
                SecretKey tmp = factory.generateSecret(spec);
                SecretKeySpec key = new SecretKeySpec(tmp.getEncoded(), "AES");
                this.v1SecretKey = key;
                return key;
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 生成一次性的随机 IV（局部变量，不写入实例字段），保证 v1 加密路径线程安全、
     * 每条密文使用独立 IV。
     */
    byte[] newRandomIv() {
        byte[] iv = new byte[isGCM() ? GCM_IV_LENGTH : AES_IV_LENGTH];
        MathHelper.secureRandom().nextBytes(iv);
        return iv;
    }

    AlgorithmParameterSpec getParams(byte[] iv) {
        if (isGCM()) {
            if (iv.length != 12) {
                byte[] newIv = new byte[12];
                System.arraycopy(iv, 0, newIv, 0, 12);
                iv = newIv;
            }
            return new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        } else {
            if (iv == null)
                return null;
            return new IvParameterSpec(iv);
        }
    }

    Cipher newEncryptCipher() throws Exception {
        Cipher cipher = Cipher.getInstance(getCipherName());
        SecretKeySpec key = this.buildSecretKey();
        AlgorithmParameterSpec spec = getParams(this.iv);
        if (spec != null) {
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        }
        return cipher;
    }

    Cipher newDecryptCipher(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(getCipherName());
        SecretKeySpec key = this.buildSecretKey();
        byte[] iv = this.iv;
        if (concatIv) {
            iv = Bytes.head(data, isGCM() ? GCM_IV_LENGTH : AES_IV_LENGTH);
        }
        AlgorithmParameterSpec spec = getParams(iv);
        if (spec != null) {
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
        } else {
            cipher.init(Cipher.DECRYPT_MODE, key);
        }
        return cipher;
    }

    @Override
    public CipherInputStream decryptInputStream(InputStream is) {
        try {
            byte[] iv = this.iv;
            // 如果设置了concatIv，则从数据中读取IV。读取的IV仅用于本流解密，
            // 不写回共享实例字段，避免并发解密多个流时相互覆盖
            if (concatIv) {
                iv = new byte[getIvLength()];
                IoHelper.readFully(is, iv);
            }
            return new CipherInputStream(is, newDecryptCipher(iv));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public CipherOutputStream encryptOutputStream(OutputStream os) {
        try {
            if (concatIv) {
                os.write(iv);
            }
            return new CipherOutputStream(os, newEncryptCipher());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    String bytesToString(byte[] bytes) {
        if (base64Encode)
            return StringHelper.encodeBase64(bytes);
        return StringHelper.bytesToHex(bytes);
    }

    byte[] stringToBytes(String str) {
        if (base64Encode)
            return StringHelper.decodeBase64(str);
        return StringHelper.hexToBytes(str);
    }

    @Override
    public String encrypt(String text) {
        if (versionedFormat) {
            return encryptVersioned(text);
        }
        return encryptLegacy(text);
    }

    /**
     * v1 自描述格式加密：每次生成新的随机 IV，PBKDF2 派生密钥，输出
     * {@code v1:} + base64/hex({@code iv || ciphertext+tag})。
     */
    String encryptVersioned(String text) {
        try {
            byte[] iv = newRandomIv();
            SecretKeySpec key = buildV1SecretKey();
            Cipher cipher = Cipher.getInstance(cipherName);
            cipher.init(Cipher.ENCRYPT_MODE, key, getParams(iv));
            byte[] cipherBytes = cipher.doFinal(text.getBytes(StringHelper.CHARSET_UTF8));
            byte[] all = Bytes.concat(iv, cipherBytes);
            return V1_MARKER + bytesToString(all);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * legacy 加密路径：静态 IV + MD5 派生密钥，仅在显式 opt-out（{@code versionedFormat=false}）
     * 时使用。保留以兼容不希望引入新格式的调用方。
     */
    String encryptLegacy(String text) {
        try {
            Cipher cipher = this.newEncryptCipher();

            byte[] byteContent = text.getBytes(StringHelper.CHARSET_UTF8);
            byte[] bytes = cipher.doFinal(byteContent);

            // 如果设置了concatIv，则加密的时候将IV拼接在加密结果的前面
            if (concatIv) {
                bytes = Bytes.concat(iv, bytes);
            }

            return bytesToString(bytes);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String decrypt(String text) {
        if (text != null && text.startsWith(V1_MARKER)) {
            return decryptVersioned(text);
        }
        return decryptLegacy(text);
    }

    /**
     * v1 解密：校验 {@code v1:} 前缀，从 payload 中切出 IV 与密文，PBKDF2 派生密钥后解密。
     * 任何篡改（GCM tag 失败）、截断或格式异常均抛出异常（fail-closed）。
     */
    String decryptVersioned(String text) {
        try {
            String payload = text.substring(V1_MARKER.length());
            byte[] data = stringToBytes(payload);

            int ivLen = isGCM() ? GCM_IV_LENGTH : AES_IV_LENGTH;
            if (data.length < ivLen) {
                throw new IllegalArgumentException("truncated v1 ciphertext: missing IV");
            }
            byte[] iv = Bytes.head(data, ivLen);
            byte[] cipherBytes = Arrays.copyOfRange(data, ivLen, data.length);

            SecretKeySpec key = buildV1SecretKey();
            Cipher cipher = Cipher.getInstance(cipherName);
            cipher.init(Cipher.DECRYPT_MODE, key, getParams(iv));
            byte[] bytes = cipher.doFinal(cipherBytes);

            return new String(bytes, StringHelper.CHARSET_UTF8);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * legacy 解密路径：读取无 {@code v1:} 前缀的密文，兼容静态 IV（{@code concatIv=false}）
     * 与前置 IV（{@code concatIv=true}）两种 legacy 形态，使用 MD5 派生密钥。
     */
    String decryptLegacy(String text) {
        try {
            byte[] byteContent = stringToBytes(text);

            Cipher cipher = this.newDecryptCipher(byteContent);

            int offset = concatIv ? getIvLength() : 0;
            byte[] bytes = cipher.doFinal(byteContent, offset, byteContent.length - offset);

            return new String(bytes, StringHelper.CHARSET_UTF8);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

}