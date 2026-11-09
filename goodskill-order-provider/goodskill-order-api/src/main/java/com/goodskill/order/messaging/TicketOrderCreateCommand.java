package com.goodskill.order.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketOrderCreateCommand implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String reservationId;
    private Long sessionId;
    private String userId;
    private String userPhone;
    private Integer quantity;
    private String sessionName;
    private BigDecimal unitPrice;
    private LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
}
