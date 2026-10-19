package com.shoppy.products;

import com.shoppy.auth.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/products")
public class ProductsController {

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    /** Only sellers (ADMIN) list products; the frontend shows the create button to them only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request,
                                  @AuthenticationPrincipal AuthUser user) {
        return productService.create(request, user.id());
    }

    @PostMapping(path = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadImage(@PathVariable Long productId,
                                           @RequestParam("image") MultipartFile image,
                                           @AuthenticationPrincipal AuthUser user) {
        return Map.of("imageUrl", productService.uploadImage(productId, image, user));
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(required = false) String status) {
        return productService.list(status);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable Long productId) {
        return productService.get(productId);
    }
}
