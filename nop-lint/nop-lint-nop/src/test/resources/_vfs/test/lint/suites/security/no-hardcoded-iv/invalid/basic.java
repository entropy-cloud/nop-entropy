import javax.crypto.spec.IvParameterSpec;

public class Basic {
    IvParameterSpec bytesIv() {
        return new IvParameterSpec(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
    }

    IvParameterSpec stringIv() {
        return new IvParameterSpec("0123456789abcdef");
    }
}
