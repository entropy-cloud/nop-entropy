local key = KEYS[1]
local value = redis.call('get',key)
local timeout = tonumber(ARGV[2])
if(value == false or value == ARGV[1] )
then
   if(timeout > 0)
   then
      redis.call('psetex', key, ARGV[2],ARGV[1]);
   else
      redis.call('set',key,ARGV[1]);
   end
end
return value;