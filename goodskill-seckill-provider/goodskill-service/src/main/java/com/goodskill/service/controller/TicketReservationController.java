package com.goodskill.service.controller;

import com.goodskill.core.util.UserInfoUtil;
import com.goodskill.service.ticket.TicketInventoryService;
import com.goodskill.service.ticket.TicketReservationRequest;
import com.goodskill.service.ticket.TicketReservationResponse;
import com.goodskill.service.ticket.TicketSessionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ticket-sessions")
@RequiredArgsConstructor
public class TicketReservationController {

    private final TicketInventoryService inventoryService;

    @GetMapping
    public List<TicketSessionView> listSessions() {
        return inventoryService.listSessions();
    }

    @GetMapping("/{sessionId}")
    public TicketSessionView getSession(@PathVariable Long sessionId) {
        return inventoryService.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/reservations")
    public ResponseEntity<TicketReservationResponse> reserve(
            @PathVariable Long sessionId,
            @Valid @RequestBody TicketReservationRequest request) {
        String userId = UserInfoUtil.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少登录用户信息");
        }
        TicketReservationResponse response = inventoryService.reserve(sessionId, userId, request);
        HttpStatus status = response.status() == TicketReservationResponse.Status.ACCEPTED
                ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

}
