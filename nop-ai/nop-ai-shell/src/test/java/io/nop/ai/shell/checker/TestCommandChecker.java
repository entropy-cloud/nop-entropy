package io.nop.ai.shell.checker;

import io.nop.ai.shell.model.Redirect;
import io.nop.ai.shell.model.SimpleCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCommandChecker {

    private static final ICommandCheckContext CTX = new ICommandCheckContext() {
        @Override
        public String workingDirectory() {
            return "/work";
        }

        @Override
        public Map<String, String> environment() {
            return Map.of("PATH", "/usr/bin");
        }

        @Override
        public boolean isRegisteredCommand(String commandName) {
            return commandName.equals("echo");
        }
    };

    private static String check(SimpleCommand command) {
        return new DefaultCommandChecker().check(command, CTX);
    }

    @Test
    public void testDefaultCommandCheckerRejectsDangerousCommands() {
        assertNotNull(check(SimpleCommand.builder("rm").arg("-rf").arg("/").build()),
                "rm -rf / must be rejected");
        assertNotNull(check(SimpleCommand.builder("rm").arg("-fr").arg("/*").build()),
                "rm -fr /* must be rejected");
        assertNotNull(check(SimpleCommand.builder("rm").arg("-f").arg("--no-preserve-root").arg("/").build()),
                "rm -f --no-preserve-root / must be rejected");
        assertNotNull(check(SimpleCommand.builder("mkfs").arg("/dev/sda").build()),
                "mkfs must be rejected");
        assertNotNull(check(SimpleCommand.builder("mkfs.ext4").arg("/dev/sda").build()),
                "mkfs.ext4 must be rejected");
        assertNotNull(check(SimpleCommand.builder("dd").arg("if=/dev/sda").arg("of=/tmp/img").build()),
                "dd must be rejected");
        assertNotNull(check(SimpleCommand.builder("shutdown").build()),
                "shutdown must be rejected");
        assertNotNull(check(SimpleCommand.builder("reboot").build()),
                "reboot must be rejected");
        assertNotNull(check(SimpleCommand.builder("init").arg("0").build()),
                "init must be rejected");
        assertNotNull(check(SimpleCommand.builder("fdisk").build()),
                "fdisk must be rejected");
    }

    @Test
    public void testDefaultCommandCheckerRejectsDeviceWrites() {
        assertNotNull(check(SimpleCommand.builder("echo").arg("x").redirect(Redirect.stdoutToFile("/dev/sda")).build()),
                "redirect to /dev/sda must be rejected");
        assertNotNull(check(SimpleCommand.builder("dd").arg("of=/dev/nvme0n1").build()),
                "write to /dev/nvme0n1 must be rejected");
        assertNotNull(check(SimpleCommand.builder("cat").arg("data").redirect(Redirect.stdoutToFile("/dev/disk2")).build()),
                "redirect to /dev/disk2 must be rejected");
    }

    @Test
    public void testDefaultCommandCheckerAllowsSafeDeviceRedirects() {
        assertNull(check(SimpleCommand.builder("echo").arg("x").redirect(Redirect.stdoutToFile("/dev/null")).build()),
                "redirect to /dev/null must be allowed");
        assertNull(check(SimpleCommand.builder("cat").arg("f").redirect(Redirect.stdoutToFile("/dev/stdout")).build()),
                "redirect to /dev/stdout must be allowed");
    }

    @Test
    public void testDefaultCommandCheckerRejectsSudoWrappedDestructive() {
        assertNotNull(check(SimpleCommand.builder("sudo").arg("rm").arg("-rf").arg("/").build()),
                "sudo rm -rf / must be rejected");
        assertNotNull(check(SimpleCommand.builder("sudo").arg("mkfs.ext4").arg("/dev/sda").build()),
                "sudo mkfs.ext4 /dev/sda must be rejected");
    }

    @Test
    public void testDefaultCommandCheckerRejectsRootWideChmodChown() {
        assertNotNull(check(SimpleCommand.builder("chmod").arg("-R").arg("777").arg("/").build()),
                "chmod -R 777 / must be rejected");
        assertNotNull(check(SimpleCommand.builder("chown").arg("-R").arg("root:root").arg("/*").build()),
                "chown -R root:root /* must be rejected");
    }

    @Test
    public void testDefaultCommandCheckerRejectsBareShellInterpreter() {
        assertNotNull(check(SimpleCommand.builder("bash").build()),
                "bare bash (piping into shell) must be rejected");
        assertNotNull(check(SimpleCommand.builder("sh").build()),
                "bare sh must be rejected");
    }

    @Test
    public void testDefaultCommandCheckerAllowsNormalCommands() {
        assertNull(check(SimpleCommand.builder("echo").arg("hello").build()));
        assertNull(check(SimpleCommand.builder("ls").arg("-l").arg("/tmp").build()));
        assertNull(check(SimpleCommand.builder("rm").arg("-f").arg("/tmp/scratch.txt").build()),
                "rm -f on a regular file must be allowed");
        assertNull(check(SimpleCommand.builder("rm").arg("-rf").arg("/tmp/scratch-dir").build()),
                "rm -rf on a non-root path must be allowed");
        assertNull(check(SimpleCommand.builder("bash").arg("script.sh").build()),
                "bash with a script argument must be allowed");
        assertNull(check(SimpleCommand.builder("sudo").arg("ls").arg("-l").build()));
    }

    @Test
    public void testCheckerContractPassReturnsNull() {
        ICommandChecker checker = (command, ctx) -> null;
        assertNull(checker.check(SimpleCommand.builder("echo").build(), CTX));
    }

    @Test
    public void testCheckerContractRejectReturnsMessage() {
        ICommandChecker checker = (command, ctx) ->
                ctx.isRegisteredCommand(command.getCommand()) ? null : "command not registered: " + command.getCommand();

        assertNull(checker.check(SimpleCommand.builder("echo").build(), CTX));

        String message = checker.check(SimpleCommand.builder("unknown").build(), CTX);
        assertTrue(message != null && message.contains("not registered"), "reject message should explain the rejection");
    }

    @Test
    public void testCheckContextContract() {
        assertEquals("/work", CTX.workingDirectory());
        assertEquals("/usr/bin", CTX.environment().get("PATH"));
        assertTrue(CTX.isRegisteredCommand("echo"));
    }
}
