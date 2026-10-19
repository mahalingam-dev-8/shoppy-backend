package com.shoppy.checkout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppy.common.ApiException;
import com.shoppy.config.AppProperties;
import com.shoppy.orders.OrdersService;
import com.shoppy.products.Product;
import com.shoppy.products.ProductEvents;
import com.shoppy.products.ProductService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.RoundingMode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ProductService productService;
    private final OrdersService ordersService;
    private final ProductEvents productEvents;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public CheckoutService(ProductService productService, OrdersService ordersService,
                           ProductEvents productEvents, ObjectMapper objectMapper, AppProperties props) {
        this.productService = productService;
        this.ordersService = ordersService;
        this.productEvents = productEvents;
        this.objectMapper = objectMapper;
        this.props = props;
        Stripe.apiKey = props.stripe().secretKey();
    }

    /** Creates a Stripe Checkout session and returns Stripe's JSON for it unchanged. */
    public String createSession(Long productId, Long userId, String successUrl, String cancelUrl) {
        requireStorefrontUrl("successUrl", successUrl);
        requireStorefrontUrl("cancelUrl", cancelUrl);

        Product product = productService.find(productId);
        if (product.isSold()) {
            throw ApiException.badRequest("Product is already sold");
        }
        // Smallest currency unit (paise for INR), rounded once. The NestJS price * 100 on a float
        // could produce 1998.9999...
        long unitAmount = product.getPrice().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("productId", String.valueOf(productId))
                .putMetadata("userId", String.valueOf(userId))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(props.stripe().currency())
                                .setUnitAmount(unitAmount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(product.getName())
                                        .setDescription(product.getDescription())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            return Session.create(params).getLastResponse().body();
        } catch (StripeException e) {
            log.error("Stripe session creation failed for product {}", productId, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment provider error");
        }
    }

    /**
     * Handles a Stripe webhook. The signature is verified against the raw body first, so a
     * forged "payment completed" request is rejected (the NestJS version accepted any body).
     * Errors other than a bad signature propagate as 500 so that Stripe retries the delivery.
     */
    public void handleWebhook(String payload, String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, props.stripe().webhookSecret());
        } catch (SignatureVerificationException e) {
            throw ApiException.badRequest("Invalid Stripe signature");
        }
        if (!"checkout.session.completed".equals(event.getType())) {
            return;
        }

        String sessionId = readSessionId(payload);
        Session session;
        try {
            session = Session.retrieve(sessionId);
        } catch (StripeException e) {
            throw new IllegalStateException("Could not load Stripe session " + sessionId, e);
        }
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Checkout session {} completed without payment ({}); no order created",
                    sessionId, session.getPaymentStatus());
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        Long productId = Long.valueOf(metadata.get("productId"));
        Long userId = Long.valueOf(metadata.get("userId"));

        boolean created;
        try {
            created = ordersService.fulfil(productId, userId);
        } catch (DataIntegrityViolationException e) {
            // A concurrent delivery of the same event created the order first.
            created = false;
        }
        if (created) {
            productEvents.publishUpdated();
        } else {
            log.info("Duplicate checkout webhook for product {} ignored", productId);
        }
    }

    private String readSessionId(String payload) {
        try {
            String id = objectMapper.readTree(payload).path("data").path("object").path("id").asText();
            if (id.isEmpty()) {
                throw ApiException.badRequest("Webhook payload has no session id");
            }
            return id;
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Malformed webhook payload");
        }
    }

    /** Stripe redirects the buyer to these URLs, so only allow the storefront's own origins. */
    private void requireStorefrontUrl(String field, String url) {
        boolean allowed = props.cors().allowedOrigins().stream()
                .anyMatch(origin -> url.equals(origin) || url.startsWith(origin + "/"));
        if (!allowed) {
            throw ApiException.badRequest(field + " must point to the storefront");
        }
    }
}
