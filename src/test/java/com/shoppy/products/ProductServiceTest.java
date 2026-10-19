package com.shoppy.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shoppy.auth.AuthUser;
import com.shoppy.common.ApiException;
import com.shoppy.storage.S3Service;
import com.shoppy.users.Role;
import com.shoppy.users.User;
import com.shoppy.users.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final long SELLER_ID = 1L;
    private static final long PRODUCT_ID = 10L;

    @Mock
    ProductRepository productRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    S3Service s3Service;

    @Mock
    ProductEvents productEvents;

    @InjectMocks
    ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product("Desk Lamp", "LED lamp", new BigDecimal("1149.00"), user(SELLER_ID, Role.ADMIN));
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
    }

    @Test
    void sellerUploadsAnImageForTheirProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(s3Service.upload(eq("products/10.png"), any(), eq("image/png")))
                .thenReturn("https://cdn.example/products/10.png");

        String url = productService.uploadImage(PRODUCT_ID, image(PNG), caller(SELLER_ID, Role.ADMIN));

        assertThat(url).isEqualTo("https://cdn.example/products/10.png");
        assertThat(product.getImageUrl()).isEqualTo(url);
        verify(productRepository).save(product);
        verify(productEvents).publishUpdated();
    }

    @Test
    void anotherSellerCannotChangeTheImage() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertForbidden(() -> productService.uploadImage(PRODUCT_ID, image(PNG), caller(2L, Role.ADMIN)));
        verifyNoInteractions(s3Service, productEvents);
    }

    @Test
    void customerCannotChangeTheImage() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertForbidden(() -> productService.uploadImage(PRODUCT_ID, image(PNG), caller(3L, Role.USER)));
        verifyNoInteractions(s3Service);
    }

    @Test
    void fileOver2MbIsRejected() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertBadRequest(
                () -> productService.uploadImage(PRODUCT_ID, image(new byte[2_000_001]), caller(SELLER_ID, Role.ADMIN)),
                "Validation failed (expected size is less than 2000000)");
        verifyNoInteractions(s3Service);
    }

    @Test
    void nonImageIsRejectedEvenWithAnImageName() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);

        assertBadRequest(
                () -> productService.uploadImage(PRODUCT_ID, image(text), caller(SELLER_ID, Role.ADMIN)),
                "Only JPEG, PNG or WebP images are allowed");
        verifyNoInteractions(s3Service);
    }

    @Test
    void emptyFileIsRejected() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertBadRequest(
                () -> productService.uploadImage(PRODUCT_ID, image(new byte[0]), caller(SELLER_ID, Role.ADMIN)),
                "image file is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"available", "availible"}) // the frontend sends the misspelling
    void availableFilterListsUnsoldProductsOnly(String status) {
        when(productRepository.findBySoldFalseOrderByIdAsc()).thenReturn(List.of(product));

        List<ProductResponse> products = productService.list(status);

        assertThat(products).extracting(ProductResponse::name).containsExactly("Desk Lamp");
        verify(productRepository, never()).findAllByOrderByIdAsc();
    }

    @Test
    void unknownProductIsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(99L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void assertBadRequest(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    private static User user(long id, Role role) {
        User user = new User("user" + id + "@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static AuthUser caller(long id, Role role) {
        return new AuthUser(id, "user" + id + "@example.com", role);
    }

    private static MockMultipartFile image(byte[] bytes) {
        return new MockMultipartFile("image", "photo.png", "image/png", bytes);
    }
}
