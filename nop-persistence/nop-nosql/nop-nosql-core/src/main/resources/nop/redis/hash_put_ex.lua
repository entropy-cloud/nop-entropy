-- Atomic hash write with TTL: HSET + PEXPIRE in one step, so the key can never be
-- observed as a TTL-less hash between the two operations (2.5-MA-08, plan 2554).
redis.call('hset', KEYS[1], ARGV[1], ARGV[2])
redis.call('pexpire', KEYS[1], ARGV[3])
return 1
