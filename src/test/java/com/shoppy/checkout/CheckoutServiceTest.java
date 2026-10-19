package com.shoppy.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppy.common.ApiException;
import com.shoppy.config.AppProperties;
import com.shoppy.orders.OrdersService;
import com.shoppy.products.Product;
import com.shoppy.products.ProductEvents;
import com.shoppy.products.ProductService;
import com.stripe.model.checkout.Session;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final String STORE = "http://localhost:3002";
    private static final String WEBHOOK_SECRET = "whsec_unit_test_secret";
    private static final String SESSION_ID = "cs_test_1";

    @Mock
    ProductService productService;

    @Mock
    OrdersService ordersService;

    @Mock
    ProductEvents productEvents;

    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.JwtProps("unused-secret-that-is-at-least-32-chars", "1d"),
                new AppProperties.CorsProps(List.of(STORE)),
                new AppProperties.StripeProps("sk_test_dummy", WEBHOOK_SECRET, "inr"),
                new AppProperties.AwsProps("us-east-1", "bucket", "", "", "", ""));
        checkoutService = new CheckoutService(productService, ordersService, productEvents, new ObjectMapper(), props);
    }

    // --- Creating a session -------------------------------------------------------------------

    @Test
    void redirectUrlOutsideTheStorefrontIsRejected() {
        // Otherwise Stripe could be used to bounce buyers to a phishing page after paying.
        assertThatThrownBy(() -> checkoutService.createSession(1L, 2L, "https://evil.example/pay", STORE + "/products/1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("successUrl must point to the storefront");
                });
        verifyNoInteractions(productService);
    }

    @Test
    void soldProductCannotBeBoughtAgain() {
        Product sold = new Product("Desk Lamp", "LED lamp", new BigDecimal("1149.00"), null);
        sold.setSold(true);
        when(productService.find(1L)).thenReturn(sold);

        assertThatThrownBy(() -> checkoutService.createSession(1L, 2L, STORE + "/checkout/success", STORE + "/products/1"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Product is already sold"));
    }

    // --- Webhook --------------------------------------------------------------------------------

    @Test
    void webhookWithAForgedSignatureIsRejected() throws Exception {
        String payload = event("checkout.session.completed");

        assertThatThrownBy(() -> checkoutService.handleWebhook(payload, sign(payload, "whsec_attacker")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Invalid Stripe signature");
                });
        verifyNoInteractions(ordersService, productEvents);
    }

    @Test
    void otherEventTypesAreIgnored() throws Exception {
        String payload = event("payment_intent.succeeded");

        checkoutService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

        verifyNoInteractions(ordersService, productEvents);
    }

    @Test
    void paidSessionCreatesTheOrderAndNotifiesShoppers() throws Exception {
        String payload = event("checkout.session.completed");
        Session session = paidSession();
        when(ordersService.fulfil(1L, 2L)).thenReturn(true);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(SESSION_ID)).thenReturn(session);
            checkoutService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));
        }

        verify(ordersService).fulfil(1L, 2L);
        verify(productEvents).publishUpdated();
    }

    @Test
    void unpaidSessionCreatesNoOrder() throws Exception {
        String payload = event("checkout.session.completed");
        Session session = mock(Session.class);
        when(session.getPaymentStatus()).thenReturn("unpaid");

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(SESSION_ID)).thenReturn(session);
            checkoutService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));
        }

        verifyNoInteractions(ordersService, productEvents);
    }

    @Test
    void duplicateDeliveryDoesNotNotifyAgain() throws Exception {
        String payload = event("checkout.session.completed");
        Session session = paidSession();
        when(ordersService.fulfil(1L, 2L)).thenReturn(false);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(SESSION_ID)).thenReturn(session);
            checkoutService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));
        }

        verify(productEvents, never()).publishUpdated();
    }

    @Test
    void concurrentDuplicateHittingTheUniqueConstraintIsTreatedAsDuplicate() throws Exception {
        String payload = event("checkout.session.completed");
        Session session = paidSession();
        when(ordersService.fulfil(1L, 2L)).thenThrow(new DataIntegrityViolationException("orders_product_id_key"));

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(SESSION_ID)).thenReturn(session);
            assertThatCode(() -> checkoutService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET)))
                    .doesNotThrowAnyException();
        }

        verify(productEvents, never()).publishUpdated();
    }

    private static Session paidSession() {
        Session session = mock(Session.class);
        when(session.getPaymentStatus()).thenReturn("paid");
        when(session.getMetadata()).thenReturn(Map.of("productId", "1", "userId", "2"));
        return session;
    }

    private static String event(String type) {
        return """
                {"id":"evt_1","object":"event","type":"%s","data":{"object":{"id":"%s","object":"checkout.session"}}}"""
                .formatted(type, SESSION_ID);
    }

    /** A Stripe-Signature header, computed the way Stripe does: HMAC-SHA256 of "timestamp.payload". */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }
}
