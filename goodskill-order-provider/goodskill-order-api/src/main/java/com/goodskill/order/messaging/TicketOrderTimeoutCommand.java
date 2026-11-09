package com.goodskill.order.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketOrderTimeoutCommand implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String reservationId;
    private LocalDateTime expiresAt;
}
