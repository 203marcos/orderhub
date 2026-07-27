package com.orderhub.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway =
            new SimulatedPaymentGateway(new BigDecimal("10000"));

    @Test
    void shouldApproveAnAmountBelowTheThreshold() {
        PaymentDecision decision = gateway.authorize(UUID.randomUUID(), new BigDecimal("9999.99"));

        assertThat(decision.approved()).isTrue();
        assertThat(decision.declineReason()).isNull();
    }

    @Test
    void shouldDeclineAtTheThreshold() {
        // The boundary itself declines — "under 10000 is approved", not "10000 or under".
        PaymentDecision decision = gateway.authorize(UUID.randomUUID(), new BigDecimal("10000"));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.declineReason()).isEqualTo(SimulatedPaymentGateway.DECLINE_REASON);
    }

    @Test
    void shouldCompareByValueSoScaleDoesNotChangeTheOutcome() {
        // BigDecimal.equals would treat 10000 and 10000.00 as different; compareTo must be used.
        assertThat(gateway.authorize(UUID.randomUUID(), new BigDecimal("10000.00")).approved()).isFalse();
    }

    @Test
    void shouldHonourAConfiguredThreshold() {
        SimulatedPaymentGateway strict = new SimulatedPaymentGateway(new BigDecimal("50"));

        assertThat(strict.authorize(UUID.randomUUID(), new BigDecimal("49")).approved()).isTrue();
        assertThat(strict.authorize(UUID.randomUUID(), new BigDecimal("51")).approved()).isFalse();
    }
}
