package com.goodskill.order.repository;

import com.goodskill.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    void deleteBySeckillId(Long seckillId);

    long countBySeckillId(Long seckillId);

    Optional<Order> findByReservationId(String reservationId);

    Optional<Order> findByReservationIdAndUserId(String reservationId, String userId);

    List<Order> findByUserPhone(String userPhone);

    Page<Order> findByUserPhoneOrderByCreateTimeDesc(String userPhone, Pageable pageable);

    Page<Order> findByUserIdOrderByCreateTimeDesc(String userId, Pageable pageable);

}
