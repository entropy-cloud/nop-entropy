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
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_ENC_KEY;
import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_IV;

public class AESTextCipher implements ITextCipher, IStreamCipher {
    public static final int GCM_TAG_LENGTH = 16;
    public static final int GCM_IV_LENGTH = 12; // 其他模式的IV长度都是16

    public static final int AES_IV_LENGTH = 16;
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

    private SecretKeySpec secretKey;
    private byte[] iv;

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
    }

    public void setEncKey(String encKey) {
        this.encKey = encKey;
        this.secretKey = null;
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

    SecretKeySpec buildSecretKey() {
        if (secretKey != null) {
            return secretKey;
        }

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
            // 如果设置了concatIv，则从数据中读取IV
            if (concatIv) {
                iv = new byte[getIvLength()];
                IoHelper.readFully(is, iv);
                this.iv = iv;
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

    public static void main(String[] args) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        String iv = "1234567890123456";
        String key = "1234567890123456";
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        byte[] input = Base64.getDecoder().decode("XUKkXg/gHviLfrsF1mHrVg==");
        String output = new String(cipher.doFinal(input), StandardCharsets.UTF_8);
        System.out.println(output);
    }
}