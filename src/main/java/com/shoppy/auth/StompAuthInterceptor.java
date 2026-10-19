package com.shoppy.auth;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Rejects STOMP connections without a valid JWT, as the Socket.io gateway disconnected them.
 * The browser cannot read the HttpOnly cookie, so the frontend passes the token (which it gets
 * from a server action) in an {@code Authentication} CONNECT header.
 */
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader(JwtCookieAuthFilter.COOKIE_NAME);
            if (token == null || jwtService.parseUserId(token).isEmpty()) {
                throw new MessageDeliveryException("Unauthorized");
            }
        }
        return message;
    }
}
