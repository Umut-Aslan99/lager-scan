package dev.umutaslan.lagerscan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.umutaslan.lagerscan.domain.DomainException;
import dev.umutaslan.lagerscan.domain.Stock;
import dev.umutaslan.lagerscan.seed.DemoData;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockRulesTest {
    private final Stock stock = new Stock(UUID.randomUUID(), "ET-1001", "A-01-01", new BigDecimal("5"));

    @Test
    void taking_more_than_is_there_is_refused_with_a_clear_message() {
        assertThatThrownBy(() -> stock.take(new BigDecimal("6"), "Stk"))
            .isInstanceOf(DomainException.class).hasMessage("Auf A-01-01 liegen nur 5 Stk.");
        assertThat(stock.getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void counting_makes_the_counted_quantity_the_truth_and_reports_the_difference() {
        assertThat(stock.count(new BigDecimal("3"))).isEqualByComparingTo("-2");
        assertThat(stock.getQuantity()).isEqualByComparingTo("3");
        assertThat(stock.count(new BigDecimal("4"))).isEqualByComparingTo("1");
    }

    @Test
    void in_house_eans_have_a_valid_check_digit() {
        var ean = DemoData.ean(1001);
        assertThat(ean).hasSize(13).startsWith("20");
        int sum = 0;
        for (int i = 0; i < 13; i++) sum += (ean.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        assertThat(sum % 10).isZero();
    }
}
