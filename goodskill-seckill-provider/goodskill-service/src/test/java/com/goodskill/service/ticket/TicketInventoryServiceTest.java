package com.goodskill.service.ticket;

import com.goodskill.order.messaging.TicketInventoryReleaseEvent;
import com.goodskill.order.messaging.TicketMessaging;
import com.goodskill.order.messaging.TicketOrderCreateCommand;
import com.goodskill.service.entity.Seckill;
import com.goodskill.service.mapper.SeckillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketInventoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SeckillMapper seckillMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TicketInventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new TicketInventoryService(redisTemplate, seckillMapper, rabbitTemplate);
        ReflectionTestUtils.setField(inventoryService, "orderTimeout", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(inventoryService, "reservationRetention", Duration.ofHours(1));
    }

    @Test
    void reservesStockAndPublishesIdempotentOrderCommand() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(seckillMapper.selectById(1000L)).thenReturn(activeSession());
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(), any(), any(), any())).thenReturn(9L);

        TicketReservationResponse response = inventoryService.reserve(
                1000L, "user-1", new TicketReservationRequest("13800138000", 1));

        assertEquals(TicketReservationResponse.Status.ACCEPTED, response.status());
        assertEquals(9, response.remainingStock());
        ArgumentCaptor<TicketOrderCreateCommand> command = ArgumentCaptor.forClass(TicketOrderCreateCommand.class);
        verify(rabbitTemplate).convertAndSend(eq(TicketMessaging.ORDER_EXCHANGE),
                eq(TicketMessaging.ORDER_CREATE_ROUTING_KEY), command.capture());
        assertEquals(response.reservationId(), command.getValue().getReservationId());
        assertEquals("user-1", command.getValue().getUserId());
    }

    @Test
    void rejectsDuplicateReservationWithoutPublishingOrder() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(seckillMapper.selectById(1000L)).thenReturn(activeSession());
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(), any(), any(), any())).thenReturn(-2L);

        TicketReservationResponse response = inventoryService.reserve(
                1000L, "user-1", new TicketReservationRequest("13800138000", 1));

        assertEquals(TicketReservationResponse.Status.DUPLICATE, response.status());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void releaseIsIdempotent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(), any(), any())).thenReturn(1L, 0L);
        TicketInventoryReleaseEvent event = TicketInventoryReleaseEvent.builder()
                .reservationId("reservation-1")
                .sessionId(1000L)
                .userId("user-1")
                .quantity(1)
                .reason("PAYMENT_TIMEOUT")
                .build();

        assertTrue(inventoryService.release(event));
        assertFalse(inventoryService.release(event));
    }

    @Test
    void compensatesReservationWhenOrderMessageCannotBePublished() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(seckillMapper.selectById(1000L)).thenReturn(activeSession());
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(), any(), any(), any())).thenReturn(9L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                any(), any(), any())).thenReturn(1L);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(eq(TicketMessaging.ORDER_EXCHANGE),
                        eq(TicketMessaging.ORDER_CREATE_ROUTING_KEY), any(TicketOrderCreateCommand.class));

        assertThrows(IllegalStateException.class, () -> inventoryService.reserve(
                1000L, "user-1", new TicketReservationRequest("13800138000", 1)));

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    private Seckill activeSession() {
        return new Seckill()
                .setSeckillId(1000L)
                .setName("演唱会晚场")
                .setNumber(10)
                .setPrice(new BigDecimal("399.00"))
                .setStartTime(Date.from(Instant.now().minusSeconds(60)))
                .setEndTime(Date.from(Instant.now().plusSeconds(3600)));
    }
}
