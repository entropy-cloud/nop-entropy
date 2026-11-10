-- Atomic hash-field computeIfAbsent: HGET + HSETNX in one script, so only one
-- concurrent loader result wins; when the field already exists the stored value is
-- returned instead of the computed one (2.5-MA-09, plan 2554).
local old = redis.call('hget', KEYS[1], ARGV[1])
if old then
  return old
end
redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2])
return ARGV[2]
