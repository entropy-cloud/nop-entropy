import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;

public class Clean {
    IvParameterSpec randomIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }
}
