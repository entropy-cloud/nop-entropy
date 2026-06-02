package io.nop.code.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.nop.api.core.exceptions.NopException;
import static io.nop.code.core.NopCodeCoreErrors.ERR_CODE_DIGEST_NOT_AVAILABLE;
public class DigestHelper {
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final int BUFFER_SIZE = 64 * 1024;

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new NopException(ERR_CODE_DIGEST_NOT_AVAILABLE, e);
        }
    }

    public static String sha256HexFromStream(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new NopException(ERR_CODE_DIGEST_NOT_AVAILABLE, e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0f]);
            sb.append(HEX_CHARS[b & 0x0f]);
        }
        return sb.toString();
    }
}
