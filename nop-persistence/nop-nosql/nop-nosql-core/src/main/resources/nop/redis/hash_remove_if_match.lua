-- Atomic hash-field remove-if-match: delete the field only when its current
-- value equals ARGV[2] (both compared in encoded text form).
if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then
  redis.call('hdel', KEYS[1], ARGV[1])
  return true
end
return false
