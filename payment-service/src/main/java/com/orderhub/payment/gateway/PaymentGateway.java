package com.orderhub.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Decides whether a charge is accepted.
 *
 * <p>This is the seam where a real acquirer would plug in. {@code PaymentService} owns *when*
 * a payment is attempted and what the Saga does with the answer; it does not own *how* the
 * answer is reached, so swapping the provider means adding a class here, not editing the
 * service.
 */
public interface PaymentGateway {

    /**
     * @param orderId the order being charged, for correlation in the provider's logs
     * @param amount  the amount to charge
     */
    PaymentDecision authorize(UUID orderId, BigDecimal amount);
}
