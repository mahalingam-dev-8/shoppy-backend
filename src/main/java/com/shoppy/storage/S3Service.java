package com.shoppy.storage;

import com.shoppy.common.ApiException;
import com.shoppy.config.AppProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores product images in AWS S3 or any S3-compatible store (Supabase Storage, Cloudflare R2,
 * MinIO). Which one is purely configuration: set S3_ENDPOINT and S3_PUBLIC_URL_BASE for a
 * compatible store, leave them empty for AWS.
 */
@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private final S3Client s3;
    private final String bucket;
    private final String publicUrlBase;

    public S3Service(AppProperties props) {
        AppProperties.AwsProps aws = props.aws();
        this.bucket = aws.s3Bucket();
        this.publicUrlBase = StringUtils.hasText(aws.publicUrlBase())
                ? trimTrailingSlash(aws.publicUrlBase())
                // Same host form as the NestJS version, which the frontend's next.config allows.
                : "https://%s.s3.amazonaws.com".formatted(bucket);

        S3ClientBuilder builder = S3Client.builder().region(Region.of(aws.region()));
        if (StringUtils.hasText(aws.endpoint())) {
            // S3-compatible stores serve buckets as paths, not subdomains.
            builder.endpointOverride(URI.create(aws.endpoint())).forcePathStyle(true);
        }
        if (StringUtils.hasText(aws.accessKeyId()) && StringUtils.hasText(aws.secretAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(aws.accessKeyId(), aws.secretAccessKey())));
        } else {
            // On AWS compute, use the instance role rather than long-lived keys.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        this.s3 = builder.build();
    }

    /** Uploads the object and returns its public URL. */
    public String upload(String key, byte[] bytes, String contentType) {
        try {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            // The provider's message names the cause (missing credentials, access denied, wrong
            // region); log it, but give the client a generic error rather than infrastructure details.
            log.error("S3 upload to bucket '{}' key '{}' failed: {}", bucket, key, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Image storage is unavailable, please try again later");
        }
        return publicUrlBase + "/" + key;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
