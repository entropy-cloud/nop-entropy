-- R4-32（2.5-C7-013，plan 2566 [R1-M6 三裁决]）：
-- ① 时间源 = Redis 服务器时钟（TIME 取秒针；跨节点绝对域禁用客户端本地钟——客户端墙钟
--    跨节点共享即 finding 本体）。存量键混存注记：旧键为客户端 epoch 秒、新键为服务器
--    epoch 秒，同域同单位；客户端钟偏斜经 delta=max(0,...) 下界钳制、回填经
--    min(capacity,...) 上界钳制，错域值影响有界。
-- ② 效果复制：TIME 为非确定性命令——脚本首行显式 redis.replicate_commands()（Redis <5.0
--    必需；>=5.0 默认 effect replication 下亦兼容）。
-- ③ 单位钉死秒：客户端原传 epoch 秒、存量键 epoch 秒，秒针不劣化（µs 可选改进不做）。
redis.replicate_commands()

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]

local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
-- now 取 Redis 服务器时间（秒）；客户端 now 参数已移除（R4-32）
local server_time = redis.call("TIME")
local now = tonumber(server_time[1])
local requested = tonumber(ARGV[3])

local fill_time = capacity/rate
local ttl = math.floor(fill_time*2)

local last_tokens = tonumber(redis.call("get", tokens_key))
if last_tokens == nil then
    last_tokens = capacity
end

local last_refreshed = tonumber(redis.call("get", timestamp_key))
if last_refreshed == nil then
    last_refreshed = 0
end

local delta = math.max(0, now-last_refreshed)
local filled_tokens = math.min(capacity, last_tokens+(delta*rate))
local allowed = filled_tokens >= requested
local new_tokens = filled_tokens
if allowed then
    new_tokens = filled_tokens - requested
end

redis.call("setex", tokens_key, ttl, new_tokens)
redis.call("setex", timestamp_key, ttl, now)

return { allowed, new_tokens }
