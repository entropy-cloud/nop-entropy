-- Atomic session write: multi-field HSET + PEXPIRE in one step, closing the historical
-- D03-02 two-step non-atomic write. ARGV holds field/value pairs followed by the TTL in
-- milliseconds as the last argument (2.5-MA-08, plan 2554).
local ttl = tonumber(ARGV[#ARGV])
for i = 1, #ARGV - 1, 2 do
  redis.call('hset', KEYS[1], ARGV[i], ARGV[i + 1])
end
if ttl > 0 then
  redis.call('pexpire', KEYS[1], ARGV[#ARGV])
end
return 1
