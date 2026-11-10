package com.goodskill.ticketing;

import com.goodskill.service.config.TicketRabbitTopologyConfiguration;
import com.goodskill.service.controller.TicketReservationController;
import com.goodskill.service.ticket.TicketInventoryMessageListener;
import com.goodskill.service.ticket.TicketInventoryService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Focused runtime for the ticket reservation flow. It deliberately excludes
 * unrelated legacy modules (Elasticsearch, Kafka, Dubbo) from component scan.
 */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@MapperScan("com.goodskill.service.mapper")
@Import({
        TicketReservationController.class,
        TicketInventoryService.class,
        TicketInventoryMessageListener.class,
        TicketRabbitTopologyConfiguration.class
})
public class TicketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
    }
}
