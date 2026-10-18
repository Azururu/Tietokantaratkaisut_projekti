package com.webstore.api.order;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter 
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {
    
    @Override
    public String convertToDatabaseColumn(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return status.getDbValue();
    }

    @Override 
    public OrderStatus convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return OrderStatus.fromDbValue(dbValue);
    }
} 
