package com.webstore.api.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotNull Integer customerId,
        Integer shippingAddressId,
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotNull Integer productId,
            @NotNull @Positive Integer quantity) {
    }
}
