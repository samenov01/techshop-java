package kz.student.shop.service;

import kz.student.shop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class StatisticsService {
    private final OrderRepository orders;
    private final ProductRepository products;

    public StatisticsService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    public Summary get() {
        var all = orders.findAll();
        var completed = all.stream().filter(order -> order.getStatus().equals("COMPLETED") && order.getPaymentStatus().equals("PAID")).toList();
        BigDecimal revenue = completed.stream().map(order -> order.getTotalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<LocalDate, BigDecimal> days = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            days.put(LocalDate.now().minusDays(i), BigDecimal.ZERO);
        }
        for (var order : completed) {
            LocalDate date = order.getCreatedAt().toLocalDate();
            if (days.containsKey(date)) {
                days.put(date, days.get(date).add(order.getTotalAmount()));
            }
        }
        return new Summary(all.size(), products.countByActiveTrue(), revenue,
                days.entrySet().stream().map(day -> new Day(day.getKey(), day.getValue())).toList());
    }

    public record Summary(long orderCount, long productCount, BigDecimal revenue, List<Day> days) {
    }

    public record Day(LocalDate date, BigDecimal amount) {
    }
}
