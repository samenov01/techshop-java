package kz.student.shop.repository;

import kz.student.shop.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    boolean existsByProductId(Long productId);

    List<CartItem> findByUserIdOrderByProductId(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
}
