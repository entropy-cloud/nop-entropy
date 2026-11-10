-- Atomic hash-field get-and-set: returns the previous value while writing the new
-- one in a single script (2.5-MA-09, plan 2554).
local old = redis.call('hget', KEYS[1], ARGV[1])
redis.call('hset', KEYS[1], ARGV[1], ARGV[2])
if old == false then old = nil end
return old
