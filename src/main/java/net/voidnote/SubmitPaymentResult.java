package net.voidnote;

/**
 * Result of {@link VoidNote#submitCryptoPayment}.
 *
 * @param credits      Updated total credit balance after the payment.
 * @param creditsAdded Number of credits added by this transaction.
 */
public record SubmitPaymentResult(int credits, int creditsAdded) {}
