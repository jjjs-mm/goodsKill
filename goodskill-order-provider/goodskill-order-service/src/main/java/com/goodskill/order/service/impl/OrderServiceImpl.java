package com.goodskill.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.goodskill.core.pojo.dto.OrderDTO;
import com.goodskill.order.entity.Order;
import com.goodskill.order.enums.OrderStatusEnum;
import com.goodskill.order.messaging.TicketInventoryReleaseEvent;
import com.goodskill.order.messaging.TicketOrderCreateCommand;
import com.goodskill.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class OrderServiceImpl {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Boolean deleteRecord(long seckillId) {
        orderRepository.deleteBySeckillId(seckillId);
        return true;
    }

    public String saveRecord(OrderDTO orderDTO) {
        Order order = Order.builder()
                .id(IdUtil.getSnowflake().nextIdStr())
                .seckillId(orderDTO.getSeckillId())
                .userPhone(orderDTO.getUserPhone())
                .status(orderDTO.getStatus())
                .createTime(orderDTO.getCreateTime())
                .serverIp(orderDTO.getServerIp())
                .userIp(orderDTO.getUserIp())
                .userId(orderDTO.getUserId())
                .goodsName(orderDTO.getGoodsName())
                .goodsTitle(orderDTO.getGoodsTitle())
                .goodsImg(orderDTO.getGoodsImg())
                .seckillPrice(orderDTO.getSeckillPrice())
                .stateDesc(orderDTO.getStateDesc())
                .build();
        orderRepository.insert(order);
        log.info("保存成功,{}", order);
        return order.getId();
    }

    /**
     * Creates an order once for a Redis reservation. Mongo's unique index on
     * reservationId closes the race between duplicated RabbitMQ deliveries.
     */
    public Order createTicketOrder(TicketOrderCreateCommand command) {
        Optional<Order> existing = orderRepository.findByReservationId(command.getReservationId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Order order = Order.builder()
                .id(IdUtil.getSnowflake().nextIdStr())
                .reservationId(command.getReservationId())
                .seckillId(command.getSessionId())
                .quantity(command.getQuantity())
                .userId(command.getUserId())
                .userPhone(command.getUserPhone())
                .goodsName(command.getSessionName())
                .seckillPrice(command.getUnitPrice() == null ? null : command.getUnitPrice().doubleValue())
                .status(OrderStatusEnum.PENDING_PAYMENT.getCode())
                .stateDesc(OrderStatusEnum.PENDING_PAYMENT.getDesc())
                .createTime(command.getReservedAt())
                .expiresAt(command.getExpiresAt())
                .build();
        try {
            return orderRepository.insert(order);
        } catch (DuplicateKeyException duplicateDelivery) {
            return orderRepository.findByReservationId(command.getReservationId()).orElseThrow(() -> duplicateDelivery);
        }
    }

    /**
     * Atomically cancels only an expired, still-unpaid order. A concurrent
     * payment changing the status to PAID wins and prevents compensation.
     */
    public Optional<TicketInventoryReleaseEvent> expirePendingOrder(String reservationId, LocalDateTime now) {
        Query query = Query.query(Criteria.where("reservationId").is(reservationId)
                .and("status").is(OrderStatusEnum.PENDING_PAYMENT.getCode())
                .and("expiresAt").lte(now));
        Update update = new Update()
                .set("status", OrderStatusEnum.CANCELLED.getCode())
                .set("stateDesc", "支付超时，订单已取消")
                .set("cancellationReason", "PAYMENT_TIMEOUT");
        Order cancelled = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Order.class);
        if (cancelled == null) {
            return Optional.empty();
        }
        return Optional.of(TicketInventoryReleaseEvent.builder()
                .reservationId(cancelled.getReservationId())
                .sessionId(cancelled.getSeckillId())
                .userId(cancelled.getUserId())
                .quantity(cancelled.getQuantity())
                .reason("PAYMENT_TIMEOUT")
                .build());
    }

    public Long count(long seckillId) {
        return orderRepository.countBySeckillId(seckillId);
    }

    public Page<Order> list(String userId, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        if (userId != null) {
            return orderRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
        }
        // 当userId为空时，返回空的分页结果
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    public Order findById(String orderId) {
        Optional<Order> order = orderRepository.findById(orderId);
        return order.orElse(null);
    }

    public boolean updateOrderStatus(String orderId, Byte status, String stateDesc, String alipayTradeNo, String timestamp) {
        Criteria criteria = Criteria.where("id").is(orderId);
        if (OrderStatusEnum.PAID.getCode().equals(status)) {
            criteria.and("status").is(OrderStatusEnum.PENDING_PAYMENT.getCode());
        }
        Update update = new Update().set("status", status).set("stateDesc", stateDesc);
        if (alipayTradeNo != null) {
            update.set("alipayTradeNo", alipayTradeNo);
        }
        if (timestamp != null && !timestamp.isEmpty()) {
            update.set("payCompleteTime",
                    LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else if (OrderStatusEnum.PAID.getCode().equals(status)) {
            update.set("payCompleteTime", LocalDateTime.now());
        }
        Order updated = mongoTemplate.findAndModify(Query.query(criteria), update,
                FindAndModifyOptions.options().returnNew(true), Order.class);
        if (updated != null) {
            log.info("更新订单状态成功: orderId={}, status={}, stateDesc={}, alipayTradeNo={}",
                    orderId, status, stateDesc, alipayTradeNo);
            return true;
        }
        log.warn("更新订单状态失败: 订单不存在或状态已变更, orderId={}", orderId);
        return false;
    }

    public Order findByReservationId(String reservationId) {
        return orderRepository.findByReservationId(reservationId).orElse(null);
    }

    public Order findByReservationId(String reservationId, String userId) {
        return orderRepository.findByReservationIdAndUserId(reservationId, userId).orElse(null);
    }

    public Page<Order> adminList(int page, int size, String orderId, Long seckillId, String userPhone, Long userId, Integer status, String startTime, String endTime) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

        Query query = new Query();

        if (orderId != null && !orderId.isEmpty()) {
            query.addCriteria(Criteria.where("id").is(orderId));
        }

        if (seckillId != null) {
            query.addCriteria(Criteria.where("seckillId").is(seckillId));
        }

        if (userPhone != null && !userPhone.isEmpty()) {
            query.addCriteria(Criteria.where("userPhone").is(userPhone));
        }

        if (userId != null) {
            query.addCriteria(Criteria.where("userId").is(userId.toString()));
        }

        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        if (startTime != null && !startTime.isEmpty()) {
            try {
                LocalDateTime startDateTime = parseDateTime(startTime);
                if (startDateTime != null) {
                    query.addCriteria(Criteria.where("createTime").gte(startDateTime));
                }
            } catch (Exception e) {
                log.warn("开始时间格式错误：startTime={}", startTime);
            }
        }

        if (endTime != null && !endTime.isEmpty()) {
            try {
                LocalDateTime endDateTime = parseDateTime(endTime);
                if (endDateTime != null) {
                    query.addCriteria(Criteria.where("createTime").lte(endDateTime));
                }
            } catch (Exception e) {
                log.warn("结束时间格式错误：endTime={}", endTime);
            }
        }

        // 优化：使用分页查询一次性获取总数和数据
        long total = mongoTemplate.count(query, Order.class);
        query.with(pageable);
        List<Order> orders = mongoTemplate.find(query, Order.class);

        return new PageImpl<>(orders, pageable, total);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTimeStr + ":00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                log.warn("时间格式解析失败：dateTimeStr={}", dateTimeStr);
                return null;
            }
        }
    }

    public Boolean deleteById(String id) {
        try {
            orderRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            log.error("删除订单失败: id={}", id, e);
            return false;
        }
    }

    public Boolean batchDelete(List<String> ids) {
        try {
            orderRepository.deleteAllById(ids);
            return true;
        } catch (Exception e) {
            log.error("批量删除订单失败: ids={}", ids, e);
            return false;
        }
    }

    public Boolean cancelOrder(String orderId, String userId) {
        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            // 验证订单是否属于当前用户
            if (order.getUserId() == null || !order.getUserId().equals(userId)) {
                log.warn("取消订单失败: 订单不属于当前用户, orderId={}, userId={}", orderId, userId);
                return false;
            }
            // 验证订单状态是否可以取消（只有待支付状态可以取消）
            if (order.getStatus() == null || !order.getStatus().equals((byte) 1)) {
                log.warn("取消订单失败: 订单状态不允许取消, orderId={}, status={}", orderId, order.getStatus());
                return false;
            }
            // 更新订单状态为已取消
            order.setStatus((byte) 3);
            order.setStateDesc("已取消");
            orderRepository.save(order);
            log.info("取消订单成功: orderId={}, userId={}", orderId, userId);
            return true;
        } else {
            log.warn("取消订单失败: 订单不存在, orderId={}", orderId);
            return false;
        }
    }
}
