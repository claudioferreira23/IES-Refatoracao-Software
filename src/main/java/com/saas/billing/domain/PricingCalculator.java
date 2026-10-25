package com.saas.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class PricingCalculator {

    private static final int ONE_YEAR_IN_MONTHS = 12;
    private static final int TWO_YEARS_IN_MONTHS = 24;
    private static final BigDecimal ONE_YEAR_LOYALTY_RATE = new BigDecimal("0.10");
    private static final BigDecimal TWO_YEARS_LOYALTY_RATE = new BigDecimal("0.15");
    private static final BigDecimal NON_PROFIT_FACTOR = new BigDecimal("0.70");

    private static final String BLACK_FRIDAY_CODE = "BLACKFRIDAY";
    private static final BigDecimal BLACK_FRIDAY_DISCOUNT = new BigDecimal("30.00");
    private static final String WELCOME_CODE = "WELCOME10";
    private static final int WELCOME_MAX_MONTHS_ACTIVE = 1;
    private static final BigDecimal WELCOME_FACTOR = new BigDecimal("0.90");

    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.001");

    public Money calculate(BillingCommand command, LocalDate today) {
        long monthsActive = monthsBetween(command.signupDate(), today);
        BigDecimal price = applyLoyaltyDiscount(command.basePrice().amount(), monthsActive);
        price = applyNonProfitDiscount(price, command.nonProfit());
        price = applyPromoCode(price, command.promoCode(), monthsActive, today);
        price = applyLateCharges(price, command.overdueDays());
        return new Money(price).atLeastZero();
    }

    private long monthsBetween(LocalDate signupDate, LocalDate today) {
        return ChronoUnit.MONTHS.between(YearMonth.from(signupDate), YearMonth.from(today));
    }

    private BigDecimal applyLoyaltyDiscount(BigDecimal basePrice, long monthsActive) {
        if (monthsActive > TWO_YEARS_IN_MONTHS) {
            return basePrice.subtract(basePrice.multiply(TWO_YEARS_LOYALTY_RATE));
        }
        if (monthsActive > ONE_YEAR_IN_MONTHS) {
            return basePrice.subtract(basePrice.multiply(ONE_YEAR_LOYALTY_RATE));
        }
        return basePrice;
    }

    private BigDecimal applyNonProfitDiscount(BigDecimal price, boolean nonProfit) {
        return nonProfit ? price.multiply(NON_PROFIT_FACTOR) : price;
    }

    private BigDecimal applyPromoCode(BigDecimal price, String promoCode, long monthsActive, LocalDate today) {
        if (BLACK_FRIDAY_CODE.equalsIgnoreCase(promoCode) && today.getMonth() == Month.NOVEMBER) {
            return price.subtract(BLACK_FRIDAY_DISCOUNT);
        }
        if (WELCOME_CODE.equalsIgnoreCase(promoCode) && monthsActive <= WELCOME_MAX_MONTHS_ACTIVE) {
            return price.multiply(WELCOME_FACTOR);
        }
        return price;
    }

    private BigDecimal applyLateCharges(BigDecimal price, int overdueDays) {
        if (overdueDays <= 0) {
            return price;
        }
        BigDecimal penalty = price.multiply(LATE_PENALTY_RATE);
        BigDecimal interest = price.multiply(DAILY_INTEREST_RATE).multiply(BigDecimal.valueOf(overdueDays));
        return price.add(penalty).add(interest);
    }
}
