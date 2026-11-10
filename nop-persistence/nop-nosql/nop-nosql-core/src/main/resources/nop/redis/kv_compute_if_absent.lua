-- Atomic computeIfAbsent write-back: GET + SET NX in one script, so only one
-- concurrent loader result wins; when the key already exists the stored value is
-- returned instead of the computed one (2.5-MA-09, plan 2554).
local old = redis.call('get', KEYS[1])
if old then
  return old
end
redis.call('set', KEYS[1], ARGV[1], 'nx')
return ARGV[1]
