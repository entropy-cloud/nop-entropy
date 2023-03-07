local value = redis.call('get', KEYS[1]);
if (value == nil) then
    return value;
else
    redis.call('pexpire',KEYS[1],ARGV[1]);
    return value;
end