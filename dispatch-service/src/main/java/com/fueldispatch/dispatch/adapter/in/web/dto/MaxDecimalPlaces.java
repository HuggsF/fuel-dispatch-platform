package com.fueldispatch.dispatch.adapter.in.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/**
 * At most {@link #value()} decimal places, ignoring trailing zeros ({@code 1.50000} has one), the
 * same rule as the domain's {@code Quantity}. {@code @Digits} counts trailing zeros, so it would
 * reject values the domain accepts. Null is valid; {@code @NotNull} reports it.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxDecimalPlaces.Validator.class)
public @interface MaxDecimalPlaces {

    int value();

    String message() default "must have at most {value} decimal places";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxDecimalPlaces, BigDecimal> {

        private int maxDecimalPlaces;

        @Override
        public void initialize(MaxDecimalPlaces annotation) {
            maxDecimalPlaces = annotation.value();
        }

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            return value == null || value.stripTrailingZeros().scale() <= maxDecimalPlaces;
        }
    }
}
