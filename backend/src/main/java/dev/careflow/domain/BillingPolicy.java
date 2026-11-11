package dev.careflow.domain;

/** Synthetic per-day pricing, NOT an NHIS fee schedule. Integer KRW, round half up per day. */
public final class BillingPolicy {
  private BillingPolicy() {}

  public static long copay(long dailyFee, int basisPoints) {
    if (dailyFee < 0 || basisPoints < 0 || basisPoints > 10000)
      throw new IllegalArgumentException("Invalid price");
    return Math.addExact(Math.multiplyExact(dailyFee, basisPoints), 5000) / 10000;
  }
}
