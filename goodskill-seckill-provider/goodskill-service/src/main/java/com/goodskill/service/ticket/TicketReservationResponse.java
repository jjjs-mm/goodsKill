package com.goodskill.service.ticket;

import java.time.LocalDateTime;

public record TicketReservationResponse(
        String reservationId,
        Long sessionId,
        Status status,
        Integer remainingStock,
        LocalDateTime expiresAt,
        String message) {

    public enum Status {
        ACCEPTED,
        DUPLICATE,
        SOLD_OUT
    }
}
