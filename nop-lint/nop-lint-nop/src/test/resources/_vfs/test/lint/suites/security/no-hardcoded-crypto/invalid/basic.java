import javax.crypto.spec.IvParameterSpec;

class Basic {
    void m() {
        javax.crypto.spec.SecretKeySpec spec = new javax.crypto.spec.SecretKeySpec("myKey123", "AES");
        new IvParameterSpec("0123456789abcdef");
    }
}
