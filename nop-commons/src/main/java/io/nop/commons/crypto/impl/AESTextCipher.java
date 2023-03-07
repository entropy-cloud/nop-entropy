/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.crypto.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.crypto.IStreamCipher;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.util.StringHelper;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;

import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_ENC_KEY;
import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_IV;
import static io.nop.commons.CommonConfigs.CFG_CRYPT_DEFAULT_SALT;

public class AESTextCipher implements ITextCipher, IStreamCipher {
    static final String DEFAULT_SALT = hashWithDefault(CFG_CRYPT_DEFAULT_SALT.get(),
            "jd987KIM^Nn3rz=sM.,4?yd6w345^(L<Xj%Q@_QWR>");
    static final byte[] DEFAULT_IV = StringHelper
            .hexToBytes(hashWithDefault(CFG_CRYPT_DEFAULT_IV.get(), "*(<K:00a9mf8ia7Nn3^y34%FER{3/"));

    static String hashWithDefault(String str, String defaultValue) {
        if (StringHelper.isEmpty(str))
            str = defaultValue;
        return StringHelper.md5Hash(str);
    }

    private boolean useCBC = true;
    private boolean noPadding = false;
    private boolean base64Encode = true;

    private String encKey = CFG_CRYPT_DEFAULT_ENC_KEY.get();

    private String saltKey = DEFAULT_SALT;

    private SecretKeySpec secretKey;
    private IvParameterSpec iv = new IvParameterSpec(DEFAULT_IV);

    public void setIv(byte[] iv) {
        this.iv = new IvParameterSpec(iv);
    }

    public void setSaltKey(String saltKey) {
        this.saltKey = saltKey;
    }

    public void setEncKey(String encKey) {
        this.encKey = encKey;
        this.secretKey = null;
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

    public AESTextCipher iv(IvParameterSpec iv) {
        this.iv = iv;
        return this;
    }

    public void setBase64Encode(boolean base64Encode) {
        this.base64Encode = base64Encode;
    }

    public void setUseCBC(boolean useCBC) {
        this.useCBC = useCBC;
    }

    public void setNoPadding(boolean noPadding) {
        this.noPadding = noPadding;
    }

    String getCipherName() {
        return useCBC ? "AES/CBC/PKCS5Padding" : (noPadding ? "AES/ECB/NoPadding" : "AES/ECB/PKCS5Padding");
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

    Cipher newEncryptCipher() throws Exception {
        Cipher cipher = Cipher.getInstance(getCipherName());
        SecretKeySpec key = this.buildSecretKey();
        if (useCBC) {
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        }
        return cipher;
    }

    Cipher newDecryptCipher() throws Exception {
        Cipher cipher = Cipher.getInstance(getCipherName());
        SecretKeySpec key = this.buildSecretKey();
        if (useCBC) {
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
        } else {
            cipher.init(Cipher.DECRYPT_MODE, key);
        }
        return cipher;
    }

    public CipherInputStream encryptInputStream(InputStream is) {
        try {
            return new CipherInputStream(is, newEncryptCipher());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public CipherInputStream decryptInputStream(InputStream is) {
        try {
            return new CipherInputStream(is, newDecryptCipher());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public CipherOutputStream encryptOutputStream(OutputStream os) {
        try {
            return new CipherOutputStream(os, newEncryptCipher());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public CipherOutputStream decryptOutputStream(OutputStream os) {
        try {
            return new CipherOutputStream(os, newDecryptCipher());
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

            return bytesToString(bytes);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String decrypt(String text) {
        try {
            Cipher cipher = this.newDecryptCipher();

            byte[] byteContent = stringToBytes(text);

            byte[] bytes = cipher.doFinal(byteContent);

            return new String(bytes, StringHelper.CHARSET_UTF8);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}