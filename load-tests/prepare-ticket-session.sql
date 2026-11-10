INSERT INTO seckill (
    seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user
) VALUES (
    9000, 'Load Test Ticket Session', 100000, NOW() - INTERVAL 1 DAY,
    NOW() + INTERVAL 30 DAY, NOW(), 9000, 199.00, '1', 'load-test'
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    number = VALUES(number),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    price = VALUES(price),
    status = VALUES(status);
