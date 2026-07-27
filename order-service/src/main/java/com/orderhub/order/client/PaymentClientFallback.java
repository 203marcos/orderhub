package com.orderhub.order.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Runs when the circuit breaker around {@link PaymentClient} opens or the call fails.
 * A payment lookup is a read, so we degrade gracefully: return the order id with an
 * {@code UNKNOWN} status instead of failing the whole request.
 */
@Component
public class PaymentClientFallback implements FallbackFactory<PaymentClient> {

    @Override
    public PaymentClient create(Throwable cause) {
        return orderId -> new PaymentClient.PaymentInfo(null, orderId, null, null, "UNKNOWN");
    }
}
