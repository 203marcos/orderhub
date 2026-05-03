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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Provider("payment-service")
@PactFolder("pacts")
@WebMvcTest(PaymentController.class)
class PaymentProviderContractTest {

    @Autowired MockMvc mockMvc;
    @MockBean PaymentService paymentService;

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
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                PaymentStatus.APPROVED,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(paymentService.getByOrderId(any())).thenReturn(response);
    }
}
