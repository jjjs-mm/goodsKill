package com.goodskill.order.messaging;

/**
 * RabbitMQ topology shared by the inventory and order services.
 */
public final class TicketMessaging {

    public static final String ORDER_EXCHANGE = "ticket.order.exchange";
    public static final String ORDER_CREATE_QUEUE = "ticket.order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "ticket.order.create";
    public static final String ORDER_CREATE_DEAD_QUEUE = "ticket.order.create.dead.queue";
    public static final String ORDER_CREATE_DEAD_ROUTING_KEY = "ticket.order.create.dead";
    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "ticket.order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "ticket.order.timeout.delay";
    public static final String ORDER_TIMEOUT_QUEUE = "ticket.order.timeout.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "ticket.order.timeout";

    public static final String INVENTORY_EXCHANGE = "ticket.inventory.exchange";
    public static final String INVENTORY_RELEASE_QUEUE = "ticket.inventory.release.queue";
    public static final String INVENTORY_RELEASE_ROUTING_KEY = "ticket.inventory.release";

    private TicketMessaging() {
    }
}
