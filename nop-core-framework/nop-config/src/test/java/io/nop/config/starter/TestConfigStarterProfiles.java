/**
 * ConfigStarter.getProfiles 回归测试：
 * 修复前首行用 CFG_PROFILE.get()（返回 nop.profile 的当前值，通常为 null）当变量名查询
 * appSource，application.yaml 中定义的 nop.profile 被静默忽略；
 * 修复后用 getName()（变量名 "nop.profile"）查询。
 */
package io.nop.config.starter;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.source.IConfigSource;
import io.nop.config.source.StaticConfigSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConfigStarterProfiles {

    static class TestableConfigStarter extends ConfigStarter {
        List<String> profiles(IConfigSource baseSource, IConfigSource appSource) {
            return getProfiles(baseSource, appSource);
        }
    }

    private static IConfigSource source(String name, Map<String, String> vars) {
        Map<String, ValueWithLocation> map = new HashMap<>();
        vars.forEach((k, v) -> map.put(k, ValueWithLocation.of(null, v)));
        return new StaticConfigSource(name, map);
    }

    @Test
    public void testProfileFromAppSourceIsHonored() {
        IConfigSource appSource = source("application.yaml", Map.of("nop.profile", "dev"));
        IConfigSource baseSource = source("nop.yaml", Map.of());

        // 修复前：appSource.getConfigValue(CFG_PROFILE.get()=null) 返回 null，
        // 回退 baseSource 空值 → 无 profile（application.yaml 的 nop.profile 被忽略）
        List<String> profiles = new TestableConfigStarter().profiles(baseSource, appSource);
        assertEquals(List.of("dev"), profiles, "application.yaml 中定义的 nop.profile 必须生效");
    }

    @Test
    public void testProfileFallsBackToBaseSource() {
        IConfigSource appSource = source("application.yaml", Map.of());
        IConfigSource baseSource = source("nop.yaml", Map.of("nop.profile", "test"));

        List<String> profiles = new TestableConfigStarter().profiles(baseSource, appSource);
        assertEquals(List.of("test"), profiles, "appSource 未定义时回退 baseSource 的 nop.profile");
    }

    @Test
    public void testProfileParentFromAppSource() {
        // CFG_PROFILE_PARENT 的变量名为 nop.profile.parent
        IConfigSource appSource = source("application.yaml", Map.of("nop.profile", "dev",
                "nop.profile.parent", "parentA,parentB"));
        IConfigSource baseSource = source("nop.yaml", Map.of());

        List<String> profiles = new TestableConfigStarter().profiles(baseSource, appSource);
        assertEquals(List.of("dev", "parentA", "parentB"), profiles, "profile 与 profile-parent 一并生效");
    }

    @Test
    public void testNoProfileYieldsEmptyList() {
        IConfigSource appSource = source("application.yaml", Map.of());
        IConfigSource baseSource = source("nop.yaml", Map.of());

        List<String> profiles = new TestableConfigStarter().profiles(baseSource, appSource);
        assertTrue(profiles.isEmpty(), "两侧均未定义 profile 时返回空列表");
    }
}
