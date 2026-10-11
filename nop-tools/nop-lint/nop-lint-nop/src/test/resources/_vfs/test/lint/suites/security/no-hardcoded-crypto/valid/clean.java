class Clean {
    void m(byte[] keyBytes) {
        javax.crypto.spec.SecretKeySpec spec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }
}
