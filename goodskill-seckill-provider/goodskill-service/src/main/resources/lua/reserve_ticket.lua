local stock = tonumber(redis.call('GET', KEYS[1]))
if not stock then
    return -3
end

if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
    return -2
end

local quantity = tonumber(ARGV[3])
if stock < quantity then
    return -1
end

local remaining = redis.call('DECRBY', KEYS[1], quantity)
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
redis.call('SET', KEYS[3], 'RESERVED', 'EX', ARGV[4])
return remaining
