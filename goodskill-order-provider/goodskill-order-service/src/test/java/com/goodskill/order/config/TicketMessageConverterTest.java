package com.goodskill.order.config;

import com.goodskill.order.messaging.TicketOrderCreateCommand;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TicketMessageConverterTest {

    @Test
    void roundTripsTicketCommandWithJavaTimeFields() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        TicketOrderCreateCommand command = TicketOrderCreateCommand.builder()
                .reservationId("reservation-1")
                .sessionId(1000L)
                .unitPrice(new BigDecimal("399.00"))
                .reservedAt(LocalDateTime.of(2026, 11, 9, 16, 0))
                .expiresAt(LocalDateTime.of(2026, 11, 9, 16, 15))
                .build();

        Message message = converter.toMessage(command, new MessageProperties());
        Object decoded = converter.fromMessage(message);

        TicketOrderCreateCommand result = assertInstanceOf(TicketOrderCreateCommand.class, decoded);
        assertEquals(command.getReservationId(), result.getReservationId());
        assertEquals(command.getExpiresAt(), result.getExpiresAt());
        assertEquals(command.getUnitPrice(), result.getUnitPrice());
    }
}
