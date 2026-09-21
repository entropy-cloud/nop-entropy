import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;

/**
 * Lock helper used by {@code ai-dev/tools/mvnq}, launched through the JEP 330
 * single-file source launcher ({@code java MvnqLock.java ...}), so no build step
 * is needed. Acquires an exclusive OS-level lock on {@code <lockfile>}, waiting up
 * to {@code <timeout-ms>}; writes holder info into the file; then runs the given
 * command with inherited stdio and exits with its exit code.
 *
 * <p>The lock is held by the open {@link FileChannel} and is released by the OS
 * when this JVM exits — including on {@code kill -9} — so a crashed holder can
 * never wedge the queue. The lock file itself is never deleted: deleting it while
 * another process holds a lock on the old inode would allow a second holder on a
 * newly created file.
 *
 * <p>Usage: {@code java MvnqLock.java <lockfile> <timeout-ms> <command> [args...]}
 * <br>Exit codes: the command's exit code, or 75 on lock-wait timeout.
 */
public class MvnqLock {
    private static final int EXIT_TIMEOUT = 75;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: MvnqLock <lockfile> <timeout-ms> <command> [args...]");
            System.exit(2);
        }
        Path lockFile = Path.of(args[0]);
        long timeoutMs = Long.parseLong(args[1]);
        String[] command = Arrays.copyOfRange(args, 2, args.length);

        Files.createDirectories(lockFile.toAbsolutePath().getParent());
        FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean announced = false;
        while (true) {
            FileLock lock = channel.tryLock();
            if (lock != null) {
                break;
            }
            if (!announced) {
                announced = true;
                System.err.println("[mvnq] queued: waiting for build lock held by " + holderInfo(lockFile));
            }
            if (System.currentTimeMillis() >= deadline) {
                System.err.println("[mvnq] ERROR: timed out after " + timeoutMs + " ms waiting for " + lockFile
                        + "; current holder: " + holderInfo(lockFile));
                System.exit(EXIT_TIMEOUT);
            }
            Thread.sleep(1000);
        }

        String holder = "pid=" + ProcessHandle.current().pid()
                + " started=" + Instant.now()
                + "\ncmd=" + String.join(" ", command)
                + "\n";
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(holder);
        channel.truncate(0);
        channel.write(buffer, 0);
        channel.force(true);

        Process child = new ProcessBuilder(command).inheritIO().start();
        System.exit(child.waitFor());
    }

    private static String holderInfo(Path lockFile) {
        try {
            String text = Files.readString(lockFile, StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? "<unknown>" : text.replace("\n", " | ");
        } catch (IOException e) {
            return "<unreadable>";
        }
    }
}
