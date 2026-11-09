package com.goodskill.service.ticket;

import java.math.BigDecimal;
import java.util.Date;

public record TicketSessionView(
        Long sessionId,
        String name,
        Integer configuredStock,
        Long availableStock,
        BigDecimal price,
        Date startTime,
        Date endTime,
        String status) {
}
