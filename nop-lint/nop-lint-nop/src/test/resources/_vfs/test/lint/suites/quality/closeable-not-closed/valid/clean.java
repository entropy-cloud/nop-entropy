import java.io.FileInputStream;
// NOTE: heap-memory types (ByteArrayOutputStream etc.) hit the wide suffix —
// known v1 FP by design (plan nop-lint/23 M4 adjudication: warning severity,
// ignore list deferred).

public class Clean {
    private FileInputStream field; // field: M1 gate exempt

    byte twr(String path) throws Exception {
        try (FileInputStream in = new FileInputStream(path)) {
            return in.read();
        }
    }

    byte explicit(String path) throws Exception {
        FileInputStream in = new FileInputStream(path);
        byte b = in.read();
        in.close();
        return b;
    }

    FileInputStream ownership(String path) throws Exception {
        FileInputStream in = new FileInputStream(path);
        return register(in);
    }

    FileInputStream register(FileInputStream in) { return in; }
}
