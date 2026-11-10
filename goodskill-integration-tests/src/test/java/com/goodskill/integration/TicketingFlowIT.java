package com.goodskill.integration;

import com.goodskill.order.messaging.TicketMessaging;
import com.goodskill.order.messaging.TicketOrderCreateCommand;
import com.goodskill.order.messaging.TicketOrderTimeoutCommand;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TicketingFlowIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.29")
            .withDatabaseName("seckill").withUsername("test").withPassword("test");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:6.0.7");

    @Test
    void reservation_timeout_and_compensation_are_consistent_across_real_infrastructure() {
        long sessionId = 9001L;
        String reservationId = "it-reservation-1";
        String userId = "it-user-1";

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("CREATE TABLE seckill (seckill_id BIGINT PRIMARY KEY, name VARCHAR(120), number INT NOT NULL)");
        jdbc.update("INSERT INTO seckill(seckill_id,name,number) VALUES (?,?,?)", sessionId, "Integration Show", 2);
        assertThat(jdbc.queryForObject("SELECT number FROM seckill WHERE seckill_id=?", Integer.class, sessionId))
                .isEqualTo(2);

        LettuceConnectionFactory connection = redisConnection();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connection);
            String stockKey = "ticket:session:" + sessionId + ":stock";
            String usersKey = "ticket:session:" + sessionId + ":users";
            String reservationKey = "ticket:reservation:" + reservationId;
            redis.opsForValue().set(stockKey, "2");
            assertThat(redis.execute(script("lua/reserve_ticket.lua"),
                    List.of(stockKey, usersKey, reservationKey), userId, reservationId, "1", "60")).isEqualTo(1L);
            assertThat(redis.execute(script("lua/reserve_ticket.lua"),
                    List.of(stockKey, usersKey, "ticket:reservation:duplicate"), userId, "duplicate", "1", "60"))
                    .isEqualTo(-2L);

            CachingConnectionFactory rabbitConnection = rabbitConnection();
            try (MongoClient mongoClient = MongoClients.create(MONGO.getReplicaSetUrl())) {
                declareTopology(rabbitConnection);
                RabbitTemplate rabbit = new RabbitTemplate(rabbitConnection);
                rabbit.setMessageConverter(new JacksonJsonMessageConverter());
                TicketOrderCreateCommand create = TicketOrderCreateCommand.builder()
                        .reservationId(reservationId).sessionId(sessionId).userId(userId)
                        .userPhone("13800138000").quantity(1).sessionName("Integration Show")
                        .unitPrice(new BigDecimal("199.00")).reservedAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusSeconds(2)).build();
                rabbit.convertAndSend(TicketMessaging.ORDER_EXCHANGE, TicketMessaging.ORDER_CREATE_ROUTING_KEY, create);
                TicketOrderCreateCommand consumed = (TicketOrderCreateCommand) rabbit.receiveAndConvert(
                        TicketMessaging.ORDER_CREATE_QUEUE, 5_000);
                assertThat(consumed).isNotNull();
                assertThat(consumed.getReservationId()).isEqualTo(reservationId);

                MongoTemplate mongo = new MongoTemplate(mongoClient, "goodskill_order_it");
                mongo.indexOps("ticket_order").ensureIndex(new Index().on("reservationId", Sort.Direction.ASC).unique());
                mongo.insert(new Document("reservationId", reservationId).append("status", "PENDING"), "ticket_order");
                assertThatThrownBy(() -> mongo.insert(
                        new Document("reservationId", reservationId).append("status", "PENDING"), "ticket_order"))
                        .isInstanceOf(DuplicateKeyException.class);

                rabbit.convertAndSend(TicketMessaging.ORDER_EXCHANGE, TicketMessaging.ORDER_TIMEOUT_DELAY_ROUTING_KEY,
                        TicketOrderTimeoutCommand.builder().reservationId(reservationId).build(), message -> {
                            message.getMessageProperties().setExpiration("500");
                            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            return message;
                        });
                TicketOrderTimeoutCommand expired = (TicketOrderTimeoutCommand) rabbit.receiveAndConvert(
                        TicketMessaging.ORDER_TIMEOUT_QUEUE, 5_000);
                assertThat(expired).isNotNull();
                assertThat(expired.getReservationId()).isEqualTo(reservationId);

                var update = mongo.getCollection("ticket_order").updateOne(
                        new Document("reservationId", reservationId).append("status", "PENDING"),
                        new Document("$set", new Document("status", "CANCELED")));
                assertThat(update.getModifiedCount()).isEqualTo(1);
            } finally {
                rabbitConnection.destroy();
            }

            assertThat(redis.execute(script("lua/release_ticket.lua"),
                    List.of(stockKey, usersKey, reservationKey), userId, reservationId, "1")).isEqualTo(1L);
            assertThat(redis.execute(script("lua/release_ticket.lua"),
                    List.of(stockKey, usersKey, reservationKey), userId, reservationId, "1")).isZero();
            assertThat(redis.opsForValue().get(stockKey)).isEqualTo("2");
        } finally {
            connection.destroy();
        }
    }

    private static LettuceConnectionFactory redisConnection() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private static CachingConnectionFactory rabbitConnection() {
        CachingConnectionFactory factory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        return factory;
    }

    private static void declareTopology(CachingConnectionFactory connection) {
        RabbitAdmin admin = new RabbitAdmin(connection);
        DirectExchange exchange = new DirectExchange(TicketMessaging.ORDER_EXCHANGE, true, false);
        Queue create = QueueBuilder.durable(TicketMessaging.ORDER_CREATE_QUEUE).build();
        Queue delay = QueueBuilder.durable(TicketMessaging.ORDER_TIMEOUT_DELAY_QUEUE)
                .deadLetterExchange(TicketMessaging.ORDER_EXCHANGE)
                .deadLetterRoutingKey(TicketMessaging.ORDER_TIMEOUT_ROUTING_KEY).build();
        Queue timeout = QueueBuilder.durable(TicketMessaging.ORDER_TIMEOUT_QUEUE).build();
        admin.declareExchange(exchange);
        admin.declareQueue(create);
        admin.declareQueue(delay);
        admin.declareQueue(timeout);
        admin.declareBinding(BindingBuilder.bind(create).to(exchange).with(TicketMessaging.ORDER_CREATE_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(delay).to(exchange).with(TicketMessaging.ORDER_TIMEOUT_DELAY_ROUTING_KEY));
        admin.declareBinding(BindingBuilder.bind(timeout).to(exchange).with(TicketMessaging.ORDER_TIMEOUT_ROUTING_KEY));
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
