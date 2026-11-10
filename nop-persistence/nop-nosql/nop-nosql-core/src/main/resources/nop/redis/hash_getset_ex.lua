-- Atomic get-and-set with TTL: returns the previous value, writes the new one and
-- refreshes the key TTL in one step (2.5-MA-08, plan 2554).
local old = redis.call('hget', KEYS[1], ARGV[1])
redis.call('hset', KEYS[1], ARGV[1], ARGV[2])
local timeout = tonumber(ARGV[3])
if timeout >= 0 then
  redis.call('pexpire', KEYS[1], ARGV[3])
end
if old == false then old = nil end
return old
