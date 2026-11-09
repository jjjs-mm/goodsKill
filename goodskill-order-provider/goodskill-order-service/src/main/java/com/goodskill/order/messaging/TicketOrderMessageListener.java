package com.goodskill.order.messaging;

import com.goodskill.order.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOrderMessageListener {

    private final OrderServiceImpl orderService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = TicketMessaging.ORDER_CREATE_QUEUE)
    public void createOrder(TicketOrderCreateCommand command) {
        orderService.createTicketOrder(command);
        long delayMillis = Math.max(1, Duration.between(LocalDateTime.now(), command.getExpiresAt()).toMillis());
        TicketOrderTimeoutCommand timeout = TicketOrderTimeoutCommand.builder()
                .reservationId(command.getReservationId())
                .expiresAt(command.getExpiresAt())
                .build();
        rabbitTemplate.convertAndSend(
                TicketMessaging.ORDER_EXCHANGE,
                TicketMessaging.ORDER_TIMEOUT_DELAY_ROUTING_KEY,
                timeout,
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                    return message;
                });
        log.info("ticket order created: reservationId={}, expiresAt={}",
                command.getReservationId(), command.getExpiresAt());
    }

    @RabbitListener(queues = TicketMessaging.ORDER_TIMEOUT_QUEUE)
    public void cancelExpiredOrder(TicketOrderTimeoutCommand command) {
        orderService.expirePendingOrder(command.getReservationId(), LocalDateTime.now())
                .ifPresent(event -> rabbitTemplate.convertAndSend(
                        TicketMessaging.INVENTORY_EXCHANGE,
                        TicketMessaging.INVENTORY_RELEASE_ROUTING_KEY,
                        event));
    }
}
