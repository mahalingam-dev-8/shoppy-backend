package com.shoppy.products;

import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Tells connected browsers that the product list changed, replacing the Socket.io
 * {@code updated} event. The frontend reacts by refreshing the page data.
 */
@Component
public class ProductEvents {

    public static final String TOPIC = "/topic/products";

    private final SimpMessagingTemplate messagingTemplate;

    public ProductEvents(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Call after the change is committed, so a client that refreshes sees it. */
    public void publishUpdated() {
        messagingTemplate.convertAndSend(TOPIC, Map.of("event", "updated"));
    }
}
