package com.shoppy.checkout;

import com.shoppy.auth.AuthUser;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/session")
    public ResponseEntity<String> createSession(@Valid @RequestBody CreateSessionRequest request,
                                                @AuthenticationPrincipal AuthUser user) {
        String sessionJson = checkoutService.createSession(
                request.productnumber(), user.id(), request.successUrl(), request.cancelUrl());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sessionJson);
    }

    /**
     * Public endpoint called by Stripe. The body is bound as a raw String: the signature is
     * computed over the exact bytes Stripe sent, so it must not be parsed and re-serialised first.
     */
    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(@RequestBody String payload,
                                        @RequestHeader("Stripe-Signature") String signature) {
        checkoutService.handleWebhook(payload, signature);
        return Map.of("received", true);
    }
}
