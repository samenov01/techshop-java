package kz.student.shop.repository;

import kz.student.shop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProductId(Long productId);

    List<OrderItem> findByOrderIdOrderByProductId(Long orderId);
}
