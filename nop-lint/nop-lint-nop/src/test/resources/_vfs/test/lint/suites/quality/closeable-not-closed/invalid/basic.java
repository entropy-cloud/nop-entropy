import java.io.FileInputStream;

public class Basic {
    void leak(String path) throws Exception {
        FileInputStream in = new FileInputStream(path);
        byte b = in.read();
    }
}
