if not redis.call('GET', KEYS[3]) then
    return 0
end

if redis.call('HGET', KEYS[2], ARGV[1]) ~= ARGV[2] then
    return 0
end

redis.call('DEL', KEYS[3])
redis.call('HDEL', KEYS[2], ARGV[1])
redis.call('INCRBY', KEYS[1], tonumber(ARGV[3]))
return 1
