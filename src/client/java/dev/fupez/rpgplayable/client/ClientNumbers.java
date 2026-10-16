package dev.fupez.rpgplayable.client;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class ClientNumbers {
    private ClientNumbers() { }
    public static String compact(BigInteger value) {
        String digits = value.toString();
        if (digits.length() <= 6) return digits;
        int exponent = digits.length() - 1;
        return digits.charAt(0) + "." + digits.substring(1, 3) + "e" + exponent;
    }
    public static double ratio(BigInteger value, BigInteger maximum) {
        if (maximum.signum() <= 0) return 0;
        if (value.signum() <= 0) return 0;
        if (value.compareTo(maximum) >= 0) return 1;
        return new BigDecimal(value).divide(new BigDecimal(maximum), 8, RoundingMode.DOWN).doubleValue();
    }
}
