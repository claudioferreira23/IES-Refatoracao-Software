package com.saas.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final LocalDate NOVEMBER_20 = LocalDate.of(2026, 11, 20);
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    private static final BigDecimal TIE_TOLERANCE = new BigDecimal("0.000000001");

    private static final String BASE_PRICE = "100.00";
    private static final String COUPON_BLACKFRIDAY = "BLACKFRIDAY";
    private static final String COUPON_WELCOME10 = "WELCOME10";

    private static final String[] SWEEP_BASE_PRICES = {"0.99", "19.90", "49.99", "99.90", "149.00", "1000.00"};
    private static final Integer[] SWEEP_MONTHS_ACTIVE = {0, 1, 2, 12, 13, 24, 25, 40};
    private static final String[] SWEEP_PROMO_CODES = {null, "", "blackfriday", COUPON_WELCOME10, "XPTO"};
    private static final Integer[] SWEEP_OVERDUE_DAYS = {0, 1, 15, 90};
    private static final Boolean[] SWEEP_NON_PROFIT = {false, true};
    private static final LocalDate[] SWEEP_TODAYS = {MARCH_15, NOVEMBER_20};
    private static final int SWEEP_COMBINATIONS = 6 * 8 * 5 * 4 * 2 * 2;

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest(name = "{0} meses de casa: {1}")
    @CsvSource({
        "6,  100.00",
        "12, 100.00",
        "13, 90.00",
        "24, 90.00",
        "25, 85.00"
    })
    void appliesLoyaltyDiscountOnlyAfterTwelveAndTwentyFourMonths(int monthsActive, String expected) {
        assertEquals(Money.of(expected), price(BASE_PRICE, MARCH_15.minusMonths(monthsActive), 0, null, false, MARCH_15));
    }

    @Test
    void countsMonthsByCalendarMonthIgnoringDayOfMonth() {
        LocalDate signup = LocalDate.of(2025, 2, 28);
        LocalDate today = LocalDate.of(2026, 3, 1);

        assertEquals(Money.of("90.00"), price(BASE_PRICE, signup, 0, null, false, today));
    }

    @ParameterizedTest(name = "ONG com {0} meses de casa: {1}")
    @CsvSource({
        "0,  70.00",
        "30, 59.50"
    })
    void appliesThirtyPercentNonProfitDiscountOnTopOfLoyaltyDiscount(int monthsActive, String expected) {
        assertEquals(Money.of(expected), price(BASE_PRICE, MARCH_15.minusMonths(monthsActive), 0, null, true, MARCH_15));
    }

    @ParameterizedTest(name = "cupom {0} em {1}: {2}")
    @CsvSource({
        "BLACKFRIDAY, 2026-11-20, 70.00",
        "BLACKFRIDAY, 2026-03-15, 100.00",
        "blackFriday, 2026-11-20, 70.00"
    })
    void appliesBlackFridayOnlyInNovemberIgnoringCase(String promoCode, LocalDate today, String expected) {
        assertEquals(Money.of(expected), price(BASE_PRICE, today, 0, promoCode, false, today));
    }

    @ParameterizedTest(name = "cupom {0} com {1} meses de casa: {2}")
    @CsvSource({
        "welcome10, 0, 90.00",
        "WELCOME10, 1, 90.00",
        "WELCOME10, 2, 100.00"
    })
    void appliesWelcomeDiscountOnlyUpToFirstMonthIgnoringCase(String promoCode, int monthsActive, String expected) {
        assertEquals(Money.of(expected), price(BASE_PRICE, MARCH_15.minusMonths(monthsActive), 0, promoCode, false, MARCH_15));
    }

    @ParameterizedTest(name = "cupom ''{0}'' em {1}")
    @CsvSource({
        "XPTO,            2026-03-15",
        "'   ',           2026-03-15",
        "' BLACKFRIDAY ', 2026-11-20"
    })
    void ignoresUnknownBlankOrPaddedPromoCodes(String promoCode, LocalDate today) {
        assertEquals(Money.of(BASE_PRICE), price(BASE_PRICE, today, 0, promoCode, false, today));
    }

    @ParameterizedTest(name = "{0} dias de atraso: {1}")
    @CsvSource({
        "10, 103.00",
        "-3, 100.00"
    })
    void addsLatePenaltyAndDailyInterestOnlyWhenOverdue(int overdueDays, String expected) {
        assertEquals(Money.of(expected), price(BASE_PRICE, MARCH_15, overdueDays, null, false, MARCH_15));
    }

    @Test
    void neverChargesNegativeAmount() {
        assertEquals(Money.ZERO, price("20.00", NOVEMBER_20, 30, COUPON_BLACKFRIDAY, false, NOVEMBER_20));
    }

    @Test
    void roundsOnlyTheFinalAmountLikeTheLegacyFormula() {
        assertEquals(Money.of("23.43"), price("19.90", MARCH_15.minusMonths(25), 365, null, false, MARCH_15));
    }

    @Test
    void matchesRoundedLegacyResultsExceptOnHalfCentTiesAcrossRuleCombinations() {
        List<SweepCase> cases = sweepCases();

        assertEquals(SWEEP_COMBINATIONS, cases.size());
        cases.forEach(this::assertMatchesRoundedLegacyResult);
    }

    private record SweepCase(String basePrice, int monthsActive, String promoCode, int overdueDays,
                             boolean nonProfit, LocalDate today) {

        LocalDate signupDate() {
            return today.minusMonths(monthsActive);
        }
    }

    private static List<SweepCase> sweepCases() {
        return Arrays.stream(SWEEP_BASE_PRICES)
                .flatMap(base -> Arrays.stream(SWEEP_MONTHS_ACTIVE)
                .flatMap(months -> Arrays.stream(SWEEP_PROMO_CODES)
                .flatMap(promo -> Arrays.stream(SWEEP_OVERDUE_DAYS)
                .flatMap(overdue -> Arrays.stream(SWEEP_NON_PROFIT)
                .flatMap(nonProfit -> Stream.of(SWEEP_TODAYS)
                .map(today -> new SweepCase(base, months, promo, overdue, nonProfit, today)))))))
                .toList();
    }

    private void assertMatchesRoundedLegacyResult(SweepCase sweepCase) {
        double legacy = legacyPrice(Double.parseDouble(sweepCase.basePrice()), sweepCase.signupDate(),
                sweepCase.overdueDays(), sweepCase.promoCode(), sweepCase.nonProfit(), sweepCase.today());
        Money actual = price(sweepCase.basePrice(), sweepCase.signupDate(), sweepCase.overdueDays(),
                sweepCase.promoCode(), sweepCase.nonProfit(), sweepCase.today());
        BigDecimal roundedLegacy = BigDecimal.valueOf(legacy).setScale(2, RoundingMode.HALF_EVEN);

        assertTrue(isAcceptedDifference(actual, roundedLegacy, legacy),
                () -> failureMessage(sweepCase, roundedLegacy, actual));
    }

    private static boolean isAcceptedDifference(Money actual, BigDecimal roundedLegacy, double legacy) {
        BigDecimal difference = actual.amount().subtract(roundedLegacy).abs();
        return difference.signum() == 0 || (isHalfCentTie(legacy) && difference.compareTo(ONE_CENT) <= 0);
    }

    private static String failureMessage(SweepCase sweepCase, BigDecimal roundedLegacy, Money actual) {
        return "base=" + sweepCase.basePrice() + " months=" + sweepCase.monthsActive()
                + " promo=" + sweepCase.promoCode() + " overdue=" + sweepCase.overdueDays()
                + " nonProfit=" + sweepCase.nonProfit() + " today=" + sweepCase.today()
                + " expected=" + roundedLegacy + " actual=" + actual;
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
            if (promoCode.equalsIgnoreCase(COUPON_BLACKFRIDAY) && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice - 30.0;
            } else if (promoCode.equalsIgnoreCase(COUPON_WELCOME10) && monthsActive <= 1) {
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
