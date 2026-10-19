package com.shoppy.orders;

import com.shoppy.auth.AuthUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final OrdersService ordersService;

    public OrdersController(OrdersService ordersService) {
        this.ordersService = ordersService;
    }

    @GetMapping
    public List<OrderResponse> myOrders(@AuthenticationPrincipal AuthUser user) {
        return ordersService.forUser(user.id());
    }

    /**
     * Orders for the products the caller listed. ADMIN is the seller role and anyone may choose
     * it at signup, so this is scoped to the caller's own products rather than every order.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderResponse> sales(@AuthenticationPrincipal AuthUser user) {
        return ordersService.forSeller(user.id());
    }
}
