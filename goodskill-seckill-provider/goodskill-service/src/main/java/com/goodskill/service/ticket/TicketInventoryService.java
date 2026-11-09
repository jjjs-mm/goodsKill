package com.goodskill.service.ticket;

import com.goodskill.order.messaging.TicketInventoryReleaseEvent;
import com.goodskill.order.messaging.TicketMessaging;
import com.goodskill.order.messaging.TicketOrderCreateCommand;
import com.goodskill.service.entity.Seckill;
import com.goodskill.service.mapper.SeckillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketInventoryService {

    private static final long SOLD_OUT = -1L;
    private static final long DUPLICATE = -2L;
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script("lua/reserve_ticket.lua");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("lua/release_ticket.lua");

    private final StringRedisTemplate redisTemplate;
    private final SeckillMapper seckillMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${ticket.order.timeout:15m}")
    private Duration orderTimeout;

    @Value("${ticket.reservation.retention:1h}")
    private Duration reservationRetention;

    public TicketReservationResponse reserve(Long sessionId, String userId, TicketReservationRequest request) {
        Seckill session = getActiveSession(sessionId);
        initializeStock(session);

        String reservationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(orderTimeout);
        long reservationTtlSeconds = Math.max(60, orderTimeout.plus(reservationRetention).toSeconds());

        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(stockKey(sessionId), usersKey(sessionId), reservationKey(reservationId)),
                userId, reservationId, String.valueOf(request.quantity()),
                String.valueOf(reservationTtlSeconds));

        if (result == null) {
            throw new IllegalStateException("Redis did not return a reservation result");
        }
        if (result == DUPLICATE) {
            return new TicketReservationResponse(null, sessionId,
                    TicketReservationResponse.Status.DUPLICATE, null, null, "同一用户不可重复预约");
        }
        if (result == SOLD_OUT) {
            return new TicketReservationResponse(null, sessionId,
                    TicketReservationResponse.Status.SOLD_OUT, 0, null, "当前场次余票不足");
        }
        if (result < 0) {
            throw new IllegalStateException("Ticket stock was not initialized");
        }

        TicketOrderCreateCommand command = TicketOrderCreateCommand.builder()
                .reservationId(reservationId)
                .sessionId(sessionId)
                .userId(userId)
                .userPhone(request.userPhone())
                .quantity(request.quantity())
                .sessionName(session.getName())
                .unitPrice(session.getPrice())
                .reservedAt(now)
                .expiresAt(expiresAt)
                .build();
        try {
            rabbitTemplate.convertAndSend(
                    TicketMessaging.ORDER_EXCHANGE,
                    TicketMessaging.ORDER_CREATE_ROUTING_KEY,
                    command);
        } catch (RuntimeException publishFailure) {
            try {
                release(TicketInventoryReleaseEvent.builder()
                        .reservationId(reservationId)
                        .sessionId(sessionId)
                        .userId(userId)
                        .quantity(request.quantity())
                        .reason("ORDER_MESSAGE_PUBLISH_FAILED")
                        .build());
            } catch (RuntimeException compensationFailure) {
                publishFailure.addSuppressed(compensationFailure);
            }
            throw publishFailure;
        }

        return new TicketReservationResponse(reservationId, sessionId,
                TicketReservationResponse.Status.ACCEPTED, result.intValue(), expiresAt,
                "库存预扣成功，订单创建中");
    }

    public boolean release(TicketInventoryReleaseEvent event) {
        Long released = redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(stockKey(event.getSessionId()), usersKey(event.getSessionId()),
                        reservationKey(event.getReservationId())),
                event.getUserId(), event.getReservationId(), String.valueOf(event.getQuantity()));
        boolean success = Long.valueOf(1L).equals(released);
        log.info("ticket inventory release: reservationId={}, reason={}, released={}",
                event.getReservationId(), event.getReason(), success);
        return success;
    }

    public TicketSessionView getSession(Long sessionId) {
        Seckill session = requireSession(sessionId);
        String cachedStock = redisTemplate.opsForValue().get(stockKey(sessionId));
        return new TicketSessionView(session.getSeckillId(), session.getName(), session.getNumber(),
                cachedStock == null ? null : Long.valueOf(cachedStock), session.getPrice(),
                session.getStartTime(), session.getEndTime(), session.getStatus());
    }

    private void initializeStock(Seckill session) {
        redisTemplate.opsForValue().setIfAbsent(
                stockKey(session.getSeckillId()), String.valueOf(session.getNumber()));
    }

    private Seckill getActiveSession(Long sessionId) {
        Seckill session = requireSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.ofInstant(session.getStartTime().toInstant(), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(session.getEndTime().toInstant(), ZoneId.systemDefault());
        if (now.isBefore(start) || now.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "票务场次不在可预约时间内");
        }
        return session;
    }

    private Seckill requireSession(Long sessionId) {
        Seckill session = seckillMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "票务场次不存在");
        }
        return session;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    private static String stockKey(Long sessionId) {
        return "ticket:session:" + sessionId + ":stock";
    }

    private static String usersKey(Long sessionId) {
        return "ticket:session:" + sessionId + ":users";
    }

    private static String reservationKey(String reservationId) {
        return "ticket:reservation:" + reservationId;
    }
}
