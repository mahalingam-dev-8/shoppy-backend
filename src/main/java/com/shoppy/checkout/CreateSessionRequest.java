package com.shoppy.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

/** Field names match what the frontend's checkout action sends, including {@code productnumber}. */
public record CreateSessionRequest(
        @NotNull(message = "productnumber must be a number") Long productnumber,

        @NotBlank(message = "successUrl should not be empty")
        @URL(message = "successUrl must be a URL address")
        String successUrl,

        @NotBlank(message = "cancelUrl should not be empty")
        @URL(message = "cancelUrl must be a URL address")
        String cancelUrl) {}
