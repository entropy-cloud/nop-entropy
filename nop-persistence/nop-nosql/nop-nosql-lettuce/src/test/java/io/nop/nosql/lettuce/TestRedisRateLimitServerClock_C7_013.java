package io.nop.nosql.lettuce;

import io.nop.nosql.core.script.RedisScripts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-32（2.5-C7-013，plan 2566 [R1-M6 三裁决]）结构钉（零依赖常跑）：
 * <ol>
 *   <li>脚本源含 redis.replicate_commands() 首行（非确定性 TIME 命令的效果复制）；</li>
 *   <li>Redis TIME 秒针取时；</li>
 *   <li>客户端 now 参数消费已移除（跨节点绝对域禁用客户端本地钟）。</li>
 * </ol>
 * 行为钉（真 Redis TIME 路径 + 旧键混存腿）由同包
 * {@code TestRedisRateLimitServerClockDocker_C7_013} 承载（testcontainers 门控，
 * 先例 TestLettuceHashTtlAtomic；环境不可用时跳过并按「结构钉+诚实声明」落账）。
 */
public class TestRedisRateLimitServerClock_C7_013 {

    @Test
    public void testScriptStructure_serverClockAndEffectReplication() {
        String script = RedisScripts.RATE_LIMIT.getText();

        // ② 脚本首个非注释行为显式效果复制（<5.0 必需，>=5.0 兼容）
        String code = script.lines()
                .map(line -> line.replaceAll("--.*$", "").trim())
                .filter(line -> !line.isEmpty())
                .findFirst().orElse("");
        assertTrue(code.startsWith("redis.replicate_commands()"),
                "RATE_LIMIT script first code line must be redis.replicate_commands() "
                        + "(non-deterministic TIME command)");

        // ① 时间源 = Redis 服务器 TIME（秒针）
        assertTrue(script.contains("redis.call(\"TIME\")") || script.contains("redis.call('TIME')"),
                "RATE_LIMIT script must take server time via redis.call('TIME')");
        assertTrue(script.contains("tonumber(server_time[1])"),
                "RATE_LIMIT script must use the seconds needle of server TIME");

        // 客户端 now 参数消费已移除（原 ARGV[3] 作 now；permits 收缩为 ARGV[3]）
        assertFalse(script.contains("local now = tonumber(ARGV"),
                "client-supplied now parameter must be removed (server clock is the source)");
    }
}
