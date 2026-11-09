package com.goodskill.service.ticket;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TicketReservationRequest(
        @NotBlank String userPhone,
        @Min(1) @Max(6) int quantity) {
}
