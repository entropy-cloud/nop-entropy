package io.nop.shell.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestOsUserHelper {

    @Test
    public void testParseWindowsGroupFromNetUserOutput() throws IOException {
        String output = "\nUser name                    john\n" +
                "Full Name                    John Smith\n" +
                "Comment\n" +
                "Account active               Yes\n" +
                "Account expires              Never\n" +
                "\n" +
                "Last logon                   Never\n" +
                "\n" +
                "Local Group Memberships      *Users\n" +
                "Global Group memberships     *None\n" +
                "The command completed successfully.\n";

        assertEquals("Users", OsUserHelper.parseWindowsGroupFromNetUserOutput(output));
    }

    @Test
    public void testParseWindowsGroupFromNetUserOutput_globalLine() throws IOException {
        // 只有 Global 行时同样可以定位
        String output = "\nUser name                    john\n" +
                "Global Group memberships     *Developers\n";
        assertEquals("Developers", OsUserHelper.parseWindowsGroupFromNetUserOutput(output));
    }

    @Test
    public void testParseWindowsGroupFromNetUserOutput_localeMismatchThrows() {
        // 中文系统/不同版本的 net user 输出没有英文标签行：抛 IOException 而不是数组越界
        String output = "\n用户名                     john\n帐户启用                  Yes\n命令成功完成。\n";
        assertThrows(IOException.class, () -> OsUserHelper.parseWindowsGroupFromNetUserOutput(output));

        assertThrows(IOException.class, () -> OsUserHelper.parseWindowsGroupFromNetUserOutput(""));
    }

    @Test
    public void testGetSudoCmd_validTenant() {
        assertEquals("sudo -u tenant1 ls", OsUserHelper.getSudoCmd("tenant1", "ls"));
        assertEquals("sudo -u tenant-01 ls", OsUserHelper.getSudoCmd("tenant-01", "ls"));
        assertEquals("ls", OsUserHelper.getSudoCmd("", "ls"));
    }

    @Test
    public void testGetSudoCmd_rejectsInjection() {
        // tenantCode 拼入 shell 命令，含空格/分号等字符时必须拒绝，防止注入额外命令
        assertThrows(IllegalArgumentException.class,
                () -> OsUserHelper.getSudoCmd("a; rm -rf /tmp/x", "ls"));
        assertThrows(IllegalArgumentException.class,
                () -> OsUserHelper.getSudoCmd("a b", "ls"));
        assertThrows(IllegalArgumentException.class,
                () -> OsUserHelper.getSudoCmd("$(whoami)", "ls"));
        assertThrows(IllegalArgumentException.class,
                () -> OsUserHelper.getSudoCmd("a`id`", "ls"));
    }
}
