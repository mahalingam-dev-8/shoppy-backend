package com.shoppy.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(JwtProps jwt, CorsProps cors, StripeProps stripe, AwsProps aws) {

    public record JwtProps(String secret, String expiration) {}

    public record CorsProps(List<String> allowedOrigins) {}

    public record StripeProps(String secretKey, String webhookSecret, String currency) {}

    /**
     * S3 settings. {@code endpoint} and {@code publicUrlBase} are empty for AWS S3 and set for an
     * S3-compatible store (Supabase Storage, Cloudflare R2, MinIO).
     */
    public record AwsProps(String region, String s3Bucket, String accessKeyId, String secretAccessKey,
                           String endpoint, String publicUrlBase) {}
}
