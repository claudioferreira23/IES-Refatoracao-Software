package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {

    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final LocalDate NOVEMBER_20 = LocalDate.of(2026, 11, 20);
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    private static final BigDecimal TIE_TOLERANCE = new BigDecimal("0.000000001");

    private final PricingCalculator calculator = new PricingCalculator();

    @Test
    void chargesBasePriceWhenNoRuleApplies() {
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15.minusMonths(6), 0, null, false, MARCH_15));
    }

    @Test
    void appliesTenPercentLoyaltyDiscountAfterTwelveMonths() {
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15.minusMonths(12), 0, null, false, MARCH_15));
        assertEquals(Money.of("90.00"), price("100.00", MARCH_15.minusMonths(13), 0, null, false, MARCH_15));
        assertEquals(Money.of("90.00"), price("100.00", MARCH_15.minusMonths(24), 0, null, false, MARCH_15));
    }

    @Test
    void appliesFifteenPercentLoyaltyDiscountAfterTwentyFourMonths() {
        assertEquals(Money.of("85.00"), price("100.00", MARCH_15.minusMonths(25), 0, null, false, MARCH_15));
    }

    @Test
    void countsMonthsByCalendarMonthIgnoringDayOfMonth() {
        LocalDate signup = LocalDate.of(2025, 2, 28);
        LocalDate today = LocalDate.of(2026, 3, 1);

        assertEquals(Money.of("90.00"), price("100.00", signup, 0, null, false, today));
    }

    @Test
    void appliesThirtyPercentNonProfitDiscount() {
        assertEquals(Money.of("70.00"), price("100.00", MARCH_15, 0, null, true, MARCH_15));
    }

    @Test
    void stacksNonProfitDiscountOnTopOfLoyaltyDiscount() {
        assertEquals(Money.of("59.50"), price("100.00", MARCH_15.minusMonths(30), 0, null, true, MARCH_15));
    }

    @Test
    void appliesBlackFridayOnlyInNovember() {
        assertEquals(Money.of("70.00"), price("100.00", NOVEMBER_20, 0, "BLACKFRIDAY", false, NOVEMBER_20));
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15, 0, "BLACKFRIDAY", false, MARCH_15));
    }

    @Test
    void matchesPromoCodesIgnoringCase() {
        assertEquals(Money.of("70.00"), price("100.00", NOVEMBER_20, 0, "blackFriday", false, NOVEMBER_20));
        assertEquals(Money.of("90.00"), price("100.00", MARCH_15, 0, "welcome10", false, MARCH_15));
    }

    @Test
    void appliesWelcomeDiscountOnlyUpToFirstMonth() {
        assertEquals(Money.of("90.00"), price("100.00", MARCH_15.minusMonths(1), 0, "WELCOME10", false, MARCH_15));
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15.minusMonths(2), 0, "WELCOME10", false, MARCH_15));
    }

    @Test
    void ignoresUnknownBlankOrPaddedPromoCodes() {
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15, 0, "XPTO", false, MARCH_15));
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15, 0, "   ", false, MARCH_15));
        assertEquals(Money.of("100.00"), price("100.00", NOVEMBER_20, 0, " BLACKFRIDAY ", false, NOVEMBER_20));
    }

    @Test
    void addsLatePenaltyAndDailyInterestWhenOverdue() {
        assertEquals(Money.of("103.00"), price("100.00", MARCH_15, 10, null, false, MARCH_15));
    }

    @Test
    void ignoresNonPositiveOverdueDays() {
        assertEquals(Money.of("100.00"), price("100.00", MARCH_15, -3, null, false, MARCH_15));
    }

    @Test
    void neverChargesNegativeAmount() {
        assertEquals(Money.ZERO, price("20.00", NOVEMBER_20, 30, "BLACKFRIDAY", false, NOVEMBER_20));
    }

    @Test
    void roundsOnlyTheFinalAmountLikeTheLegacyFormula() {
        assertEquals(Money.of("23.43"), price("19.90", MARCH_15.minusMonths(25), 365, null, false, MARCH_15));
    }

    @Test
    void matchesRoundedLegacyResultsExceptOnHalfCentTiesAcrossRuleCombinations() {
        String[] basePrices = {"0.99", "19.90", "49.99", "99.90", "149.00", "1000.00"};
        int[] monthsActive = {0, 1, 2, 12, 13, 24, 25, 40};
        String[] promoCodes = {null, "", "blackfriday", "WELCOME10", "XPTO"};
        int[] overdueDays = {0, 1, 15, 90};
        LocalDate[] todays = {MARCH_15, NOVEMBER_20};

        for (String base : basePrices) {
            for (int months : monthsActive) {
                for (String promo : promoCodes) {
                    for (int overdue : overdueDays) {
                        for (boolean nonProfit : new boolean[] {false, true}) {
                            for (LocalDate today : todays) {
                                LocalDate signup = today.minusMonths(months);
                                double expected = legacyPrice(Double.parseDouble(base), signup, overdue, promo, nonProfit, today);
                                Money actual = price(base, signup, overdue, promo, nonProfit, today);
                                BigDecimal roundedLegacy = BigDecimal.valueOf(expected).setScale(2, RoundingMode.HALF_EVEN);
                                BigDecimal difference = actual.amount().subtract(roundedLegacy).abs();
                                boolean allowed = difference.signum() == 0
                                        || (isHalfCentTie(expected) && difference.compareTo(ONE_CENT) <= 0);
                                assertTrue(allowed,
                                        () -> "base=" + base + " months=" + months + " promo=" + promo
                                                + " overdue=" + overdue + " nonProfit=" + nonProfit
                                                + " today=" + today + " expected=" + roundedLegacy + " actual=" + actual);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isHalfCentTie(double legacyValue) {
        BigDecimal value = BigDecimal.valueOf(legacyValue);
        BigDecimal thousandths = value.setScale(3, RoundingMode.HALF_EVEN);
        return value.subtract(thousandths).abs().compareTo(TIE_TOLERANCE) <= 0
                && thousandths.unscaledValue().abs().intValue() % 10 == 5;
    }

    private Money price(String basePrice, LocalDate signupDate, int overdueDays, String promoCode,
                        boolean nonProfit, LocalDate today) {
        BillingCommand command = new BillingCommand(
                "cus_1", Money.of(basePrice), signupDate, overdueDays, promoCode, nonProfit, "tok_1");
        return calculator.calculate(command, today);
    }

    private static double legacyPrice(double basePrice, LocalDate signupDate, int overdueDays, String promoCode,
                                      boolean isNonProfit, LocalDate today) {
        int monthsActive = (today.getYear() - signupDate.getYear()) * 12
                + (today.getMonthValue() - signupDate.getMonthValue());
        double calculatedPrice = basePrice;
        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice - (basePrice * 0.15);
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice - (basePrice * 0.10);
        }
        if (isNonProfit) {
            calculatedPrice = calculatedPrice * 0.70;
        }
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice - 30.0;
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice * 0.90;
            }
        }
        if (overdueDays > 0) {
            double penalty = calculatedPrice * 0.02;
            double dailyInterest = (calculatedPrice * 0.001) * overdueDays;
            calculatedPrice = calculatedPrice + penalty + dailyInterest;
        }
        if (calculatedPrice < 0) {
            calculatedPrice = 0.0;
        }
        return calculatedPrice;
    }
}
