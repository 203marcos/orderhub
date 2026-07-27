package com.orderhub.payment.contract;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import com.orderhub.payment.controller.PaymentController;
import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies that this service still satisfies the contract order-service depends on.
 *
 * <p>The contract is produced by the consumer's test and copied here by the build, so it
 * lives under {@code target/} — generated state, wiped by {@code mvn clean} like anything
 * else. Keeping it outside {@code target/} once meant a stale copy could linger locally and
 * hide the fact that CI, starting clean, had nothing to verify against.
 *
 * <p>Run {@code mvn test -pl order-service -Dtest=PaymentServiceContractTest} first, then copy
 * {@code order-service/target/pacts/*.json} here — that is exactly what the pipeline does.
 */
@Provider("payment-service")
@PactFolder("target/pacts")
@WebMvcTest(PaymentController.class)
class PaymentProviderContractTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentService paymentService;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("payment exists for order")
    void paymentExistsForOrder() {
        UUID orderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID userId = UUID.fromString("9f8b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d");
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                orderId,
                userId,
                new BigDecimal("99.99"),
                PaymentStatus.APPROVED,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(paymentService.getByOrderId(any(), any())).thenReturn(response);
    }
}
