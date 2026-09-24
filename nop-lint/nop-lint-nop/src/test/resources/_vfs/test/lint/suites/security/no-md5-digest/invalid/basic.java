package demo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Bad {
    MessageDigest md5() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5");
    }

    MessageDigest md5Lower() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("md5");
    }
}
