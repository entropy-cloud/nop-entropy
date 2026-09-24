package demo;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;

class Bad {
    Cipher des() throws NoSuchAlgorithmException, NoSuchPaddingException {
        return Cipher.getInstance("DES");
    }

    KeyGenerator desede() throws NoSuchAlgorithmException {
        return KeyGenerator.getInstance("DESede");
    }
}
