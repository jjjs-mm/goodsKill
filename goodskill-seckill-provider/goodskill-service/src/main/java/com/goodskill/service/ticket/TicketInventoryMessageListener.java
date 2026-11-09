package com.goodskill.service.ticket;

import com.goodskill.order.messaging.TicketInventoryReleaseEvent;
import com.goodskill.order.messaging.TicketMessaging;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketInventoryMessageListener {

    private final TicketInventoryService inventoryService;

    @RabbitListener(queues = TicketMessaging.INVENTORY_RELEASE_QUEUE)
    public void releaseInventory(TicketInventoryReleaseEvent event) {
        inventoryService.release(event);
    }
}
