package com.orderhub.payment.gateway;

/**
 * A gateway's answer. Carries the decline reason with the decision so a caller cannot read
 * one without the other, and so "declined" is never a bare boolean with the reason invented
 * somewhere else.
 */
public record PaymentDecision(boolean approved, String declineReason) {

    private static final PaymentDecision APPROVED = new PaymentDecision(true, null);

    public static PaymentDecision approve() {
        return APPROVED;
    }

    public static PaymentDecision decline(String reason) {
        return new PaymentDecision(false, reason);
    }
}
