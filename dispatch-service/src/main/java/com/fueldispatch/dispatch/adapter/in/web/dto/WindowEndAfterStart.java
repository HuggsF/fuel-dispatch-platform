package com.fueldispatch.dispatch.adapter.in.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BR-2 on the request: {@code windowEnd} must be after {@code windowStart}. Reported on the {@code
 * windowEnd} field; skipped while either instant is missing, which {@code @NotNull} reports.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WindowEndAfterStart.Validator.class)
public @interface WindowEndAfterStart {

    String message() default "must be after windowStart";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WindowEndAfterStart, CreateOrderRequest> {

        @Override
        public boolean isValid(CreateOrderRequest request, ConstraintValidatorContext context) {
            if (request.windowStart() == null
                    || request.windowEnd() == null
                    || request.windowEnd().isAfter(request.windowStart())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("windowEnd")
                    .addConstraintViolation();
            return false;
        }
    }
}
