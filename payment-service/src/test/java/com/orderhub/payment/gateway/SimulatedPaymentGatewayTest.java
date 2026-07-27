package com.orderhub.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulatedPaymentGateway")
class SimulatedPaymentGatewayTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("10000");

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(THRESHOLD);

    /**
     * The rule is "below the threshold is approved", so the threshold itself declines. The
     * boundary rows are the ones that matter: an off-by-one here silently changes which
     * orders the Saga takes down its failure path.
     *
     * <p>{@code 10000.00} is present on purpose — {@code BigDecimal.equals} would treat it as
     * different from {@code 10000}, so this row fails if anyone swaps {@code compareTo} for
     * {@code equals}.
     */
    @ParameterizedTest(name = "{0} -> approved={1}")
    @CsvSource({
            "0.01,     true",
            "9999.99,  true",
            "10000,    false",
            "10000.00, false",
            "10000.01, false",
            "99999,    false"
    })
    @DisplayName("approves strictly below the threshold")
    void shouldDecideByTheThreshold(BigDecimal amount, boolean expectedApproval) {
        PaymentDecision decision = gateway.authorize(UUID.randomUUID(), amount);

        assertThat(decision.approved()).isEqualTo(expectedApproval);
    }

    @Test
    @DisplayName("gives no decline reason when it approves")
    void shouldNotAttachAReasonToAnApproval() {
        PaymentDecision decision = gateway.authorize(UUID.randomUUID(), new BigDecimal("10"));

        assertThat(decision.declineReason()).isNull();
    }

    @Test
    @DisplayName("always explains a decline")
    void shouldExplainADecline() {
        // PaymentService copies this straight into the PaymentFailed event, which is what the
        // customer eventually sees — it can never be null.
        PaymentDecision decision = gateway.authorize(UUID.randomUUID(), new BigDecimal("20000"));

        assertThat(decision.declineReason()).isEqualTo(SimulatedPaymentGateway.DECLINE_REASON);
    }

    @Test
    @DisplayName("honours a configured threshold")
    void shouldHonourAConfiguredThreshold() {
        // The ceiling is a property, not a constant: this proves it is actually read.
        SimulatedPaymentGateway strict = new SimulatedPaymentGateway(new BigDecimal("50"));

        assertThat(strict.authorize(UUID.randomUUID(), new BigDecimal("49")).approved()).isTrue();
        assertThat(strict.authorize(UUID.randomUUID(), new BigDecimal("51")).approved()).isFalse();
    }
}
