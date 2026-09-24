package demo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Clean {
    MessageDigest strong() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }
}
