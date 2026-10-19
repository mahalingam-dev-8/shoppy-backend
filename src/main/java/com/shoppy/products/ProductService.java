package com.shoppy.products;

import com.shoppy.auth.AuthUser;
import com.shoppy.common.ApiException;
import com.shoppy.storage.S3Service;
import com.shoppy.users.Role;
import com.shoppy.users.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

    static final long MAX_IMAGE_BYTES = 2_000_000;

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final ProductEvents productEvents;

    public ProductService(ProductRepository productRepository, UserRepository userRepository,
                          S3Service s3Service, ProductEvents productEvents) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.s3Service = s3Service;
        this.productEvents = productEvents;
    }

    public ProductResponse create(CreateProductRequest request, Long userId) {
        Product product = new Product(
                request.name().trim(),
                request.description().trim(),
                request.price(),
                userRepository.getReferenceById(userId));
        Product saved = productRepository.save(product);
        productEvents.publishUpdated();
        return ProductResponse.from(saved);
    }

    /**
     * {@code status=available} lists unsold products only. The frontend currently sends the
     * misspelling "availible", which NestJS silently ignored (so it listed everything); both
     * spellings are accepted here so the storefront hides sold items as intended.
     */
    public List<ProductResponse> list(String status) {
        boolean availableOnly = "available".equalsIgnoreCase(status) || "availible".equalsIgnoreCase(status);
        List<Product> products = availableOnly
                ? productRepository.findBySoldFalseOrderByIdAsc()
                : productRepository.findAllByOrderByIdAsc();
        return products.stream().map(ProductResponse::from).toList();
    }

    public ProductResponse get(Long productId) {
        return ProductResponse.from(find(productId));
    }

    public Product find(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found for id " + productId));
    }

    /** Only the seller (or an admin) may set a product's image. */
    public String uploadImage(Long productId, MultipartFile file, AuthUser caller) {
        Product product = find(productId);
        boolean isOwner = product.getUser().getId().equals(caller.id());
        if (!isOwner && caller.role() != Role.ADMIN) {
            throw ApiException.forbidden("You can only upload images for your own products");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("Validation failed (expected size is less than 2000000)");
        }

        byte[] bytes = readBytes(file);
        ImageType type = ImageType.detect(bytes)
                .orElseThrow(() -> ApiException.badRequest("Only JPEG, PNG or WebP images are allowed"));

        String url = s3Service.upload("products/" + productId + "." + type.extension(), bytes, type.contentType());
        product.setImageUrl(url);
        productRepository.save(product);
        productEvents.publishUpdated();
        return url;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded image", e);
        }
    }
}
