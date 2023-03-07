if redis.call('get', KEYS[1]) == ARGV[1]  then
  redis.call('del', KEYS[1]);
  return true;
else
  return false;
end;