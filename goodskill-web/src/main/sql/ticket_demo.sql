USE seckill;

INSERT INTO seckill (
    seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user
) VALUES
    (9101, '新世界巡回演唱会·上海站', 680, NOW() - INTERVAL 1 DAY,
     NOW() + INTERVAL 90 DAY, NOW(), 9101, 680.00, '1', 'ticket-demo'),
    (9102, '星际漫游音乐节·双日通票', 1200, NOW() - INTERVAL 1 DAY,
     NOW() + INTERVAL 120 DAY, NOW(), 9102, 499.00, '1', 'ticket-demo'),
    (9103, '「回声」Livehouse 限定专场', 320, NOW() - INTERVAL 1 DAY,
     NOW() + INTERVAL 60 DAY, NOW(), 9103, 299.00, '1', 'ticket-demo')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    number = VALUES(number),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    price = VALUES(price),
    status = VALUES(status),
    create_user = VALUES(create_user);
