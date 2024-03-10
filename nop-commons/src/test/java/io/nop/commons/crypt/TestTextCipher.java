/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.crypt;

import io.nop.commons.crypto.IStreamCipher;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTextCipher {

    @Test
    public void testEncrypt2() {
        String authKey = "abadj;l4j5;w34j5w345w345";
        AESTextCipher enc = new AESTextCipher();
        enc.encKey(authKey);
        String code = enc.encrypt("aaa3444444444444444444444444444444444444");
        System.out.println(code);

        ITextCipher dec = new AESTextCipher();
        dec.encKey(authKey);
        String result = dec.decrypt(code);
        assertEquals(result, "aaa3444444444444444444444444444444444444");

        enc.setBase64Encode(false);
        code = enc.encrypt(StringHelper.repeat("123456789", 500));
        System.out.println(code);
    }

    @Test
    public void testEncrypt() {
        ITextCipher enc = new AESTextCipher();
        String code = enc.encrypt("aaa");
        String decode = enc.decrypt(code);
        assertEquals(decode, "aaa");
    }

    @Test
    public void testCipherStream() throws Exception {
        IStreamCipher enc = new AESTextCipher();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStream cos = enc.encryptOutputStream(os);
        cos.write(StringHelper.repeat("abc", 800).getBytes());
        cos.close();

        byte[] data = os.toByteArray();

        ByteArrayInputStream is = new ByteArrayInputStream(data);
        InputStream cis = enc.decryptInputStream(is);
        String text = IoHelper.readText(cis, "UTF-8");
        assertEquals(StringHelper.repeat("abc", 800), text);
    }
}
