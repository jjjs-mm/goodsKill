package com.goodskill.order.config;

import com.goodskill.order.messaging.TicketMessaging;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketOrderRabbitConfiguration {

    @Bean
    MessageConverter ticketMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    DirectExchange ticketOrderExchange() {
        return new DirectExchange(TicketMessaging.ORDER_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange ticketInventoryExchange() {
        return new DirectExchange(TicketMessaging.INVENTORY_EXCHANGE, true, false);
    }

    @Bean
    Queue ticketOrderCreateQueue() {
        return QueueBuilder.durable(TicketMessaging.ORDER_CREATE_QUEUE)
                .deadLetterExchange(TicketMessaging.ORDER_EXCHANGE)
                .deadLetterRoutingKey(TicketMessaging.ORDER_CREATE_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue ticketOrderCreateDeadQueue() {
        return QueueBuilder.durable(TicketMessaging.ORDER_CREATE_DEAD_QUEUE).build();
    }

    @Bean
    Queue ticketOrderTimeoutDelayQueue() {
        return QueueBuilder.durable(TicketMessaging.ORDER_TIMEOUT_DELAY_QUEUE)
                .deadLetterExchange(TicketMessaging.ORDER_EXCHANGE)
                .deadLetterRoutingKey(TicketMessaging.ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue ticketOrderTimeoutQueue() {
        return QueueBuilder.durable(TicketMessaging.ORDER_TIMEOUT_QUEUE).build();
    }

    @Bean
    Binding ticketOrderCreateBinding(Queue ticketOrderCreateQueue, DirectExchange ticketOrderExchange) {
        return BindingBuilder.bind(ticketOrderCreateQueue)
                .to(ticketOrderExchange)
                .with(TicketMessaging.ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    Binding ticketOrderCreateDeadBinding(Queue ticketOrderCreateDeadQueue,
                                         DirectExchange ticketOrderExchange) {
        return BindingBuilder.bind(ticketOrderCreateDeadQueue)
                .to(ticketOrderExchange)
                .with(TicketMessaging.ORDER_CREATE_DEAD_ROUTING_KEY);
    }

    @Bean
    Binding ticketOrderTimeoutDelayBinding(Queue ticketOrderTimeoutDelayQueue,
                                           DirectExchange ticketOrderExchange) {
        return BindingBuilder.bind(ticketOrderTimeoutDelayQueue)
                .to(ticketOrderExchange)
                .with(TicketMessaging.ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    Binding ticketOrderTimeoutBinding(Queue ticketOrderTimeoutQueue, DirectExchange ticketOrderExchange) {
        return BindingBuilder.bind(ticketOrderTimeoutQueue)
                .to(ticketOrderExchange)
                .with(TicketMessaging.ORDER_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    Queue ticketInventoryReleaseQueue() {
        return QueueBuilder.durable(TicketMessaging.INVENTORY_RELEASE_QUEUE).build();
    }

    @Bean
    Binding ticketInventoryReleaseBinding(Queue ticketInventoryReleaseQueue,
                                          DirectExchange ticketInventoryExchange) {
        return BindingBuilder.bind(ticketInventoryReleaseQueue)
                .to(ticketInventoryExchange)
                .with(TicketMessaging.INVENTORY_RELEASE_ROUTING_KEY);
    }
}
