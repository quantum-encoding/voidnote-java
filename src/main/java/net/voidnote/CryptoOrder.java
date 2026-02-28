package net.voidnote;

/**
 * Result of {@link VoidNote#createCryptoOrder}.
 *
 * @param orderId   Order ID — pass to {@link VoidNote#submitCryptoPayment} after the transfer.
 * @param toAddress On-chain recipient address.
 * @param chain     Chain name (e.g. {@code "polygon"}, {@code "bitcoin"}, {@code "tron"}).
 * @param token     Token symbol (e.g. {@code "USDT"}, {@code "ETH"}, {@code "BTC"}).
 * @param amountUsd USD value of the order.
 * @param amount    Exact amount to send (formatted string for display, e.g. {@code "20.000000"}).
 * @param credits   Credits to be added on confirmation.
 * @param expiresAt ISO 8601 timestamp — order expires if not paid by this time.
 */
public record CryptoOrder(
        String orderId,
        String toAddress,
        String chain,
        String token,
        double amountUsd,
        String amount,
        int credits,
        String expiresAt
) {}
