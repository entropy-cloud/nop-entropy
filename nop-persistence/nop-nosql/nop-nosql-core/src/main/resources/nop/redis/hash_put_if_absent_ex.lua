-- Atomic put-if-absent with TTL: HSETNX + PEXPIRE in one step. Returns true when
-- the field was absent and has been set (TTL applied), false when the field already
-- existed (value and TTL left untouched), preserving the HSETNX return contract.
local set = redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2])
if set == 1 and tonumber(ARGV[3]) >= 0 then
  redis.call('pexpire', KEYS[1], ARGV[3])
end
return set
