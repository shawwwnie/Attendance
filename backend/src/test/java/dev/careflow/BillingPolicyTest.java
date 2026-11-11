package dev.careflow;

import static org.assertj.core.api.Assertions.*;

import dev.careflow.domain.BillingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillingPolicyTest {
  @ParameterizedTest
  @CsvSource({
    "60000,1500,9000",
    "55000,900,4950",
    "1003,1500,150",
    "1005,1500,151",
    "1000,0,0",
    "1000,10000,1000"
  })
  void roundsIntegerKrwHalfUpPerVisit(long fee, int rate, long expected) {
    assertThat(BillingPolicy.copay(fee, rate)).isEqualTo(expected);
  }

  @Test
  void rejectsInvalidRatesAndOverflow() {
    assertThatThrownBy(() -> BillingPolicy.copay(1000, 10001))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BillingPolicy.copay(Long.MAX_VALUE, 1500))
        .isInstanceOf(ArithmeticException.class);
  }
}
