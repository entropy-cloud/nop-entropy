-- Set a session hash field only when the session key still exists, so a late
-- write cannot resurrect an expired/deleted session as a TTL-less key.
if redis.call('exists', KEYS[1]) == 0 then
  return 0
end
redis.call('hset', KEYS[1], ARGV[1], ARGV[2])
return 1
