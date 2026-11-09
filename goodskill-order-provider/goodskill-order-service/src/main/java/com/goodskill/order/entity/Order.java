package com.goodskill.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
public class Order {

    @Id
    private String id;

    /**
     * Redis reservation id. It is the idempotency key of asynchronous order creation.
     */
    @Indexed(unique = true, sparse = true)
    private String reservationId;

    /**
     * 秒杀活动id
     */
    private Long seckillId;

    private Integer quantity;

    /**
     * 用户手机号
     */
    private String userPhone;

    /**
     * 状态
     */
    private Byte status;

    private LocalDateTime createTime;

    private LocalDateTime expiresAt;

    /**
     * 服务器ip
     */
    private String serverIp;

    /**
     * 用户ip
     */
    private String userIp;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品标题
     */
    private String goodsTitle;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 秒杀价格
     */
    private Double seckillPrice;

    /**
     * 状态描述
     */
    private String stateDesc;

    /**
     * 支付宝订单号
     */
    private String alipayTradeNo;

    /**
     * 支付完成时间
     */
    private LocalDateTime payCompleteTime;

    private String cancellationReason;

}
