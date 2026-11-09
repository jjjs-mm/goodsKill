package com.goodskill.order.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketInventoryReleaseEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String reservationId;
    private Long sessionId;
    private String userId;
    private Integer quantity;
    private String reason;
}
