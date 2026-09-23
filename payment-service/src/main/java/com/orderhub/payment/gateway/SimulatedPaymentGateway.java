package com.orderhub.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stands in for a real acquirer: anything at or above a configurable ceiling is declined,
 * everything else is approved. Deterministic on purpose, so the Saga's failure path is easy
 * to exercise end to end without a sandbox account.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    static final String DECLINE_REASON = "Insufficient funds";

    private final BigDecimal declineThreshold;

    public SimulatedPaymentGateway(
            @Value("${payment.simulated.decline-threshold:10000}") BigDecimal declineThreshold) {
        this.declineThreshold = declineThreshold;
    }

    @Override
    public PaymentDecision authorize(UUID orderId, BigDecimal amount) {
        return amount.compareTo(declineThreshold) < 0
                ? PaymentDecision.approve()
                : PaymentDecision.decline(DECLINE_REASON);
    }
}
