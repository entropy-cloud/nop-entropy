-- Atomic hash-field put-if-absent-or-match: set the field to ARGV[2] when it is
-- absent or its current value equals ARGV[2]; returns the previous raw value.
local key = KEYS[1]
local field = ARGV[1]
local value = redis.call('hget', key, field)
local timeout = tonumber(ARGV[3])

if value == false or value == ARGV[2] then
   redis.call('hset', key, field, ARGV[2])
   if timeout > 0 then
      redis.call('pexpire', key, ARGV[3])
   end
end

if value == false then value = nil end
return value
