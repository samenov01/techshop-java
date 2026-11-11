package kz.student.shop.repository;

import kz.student.shop.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Modifying
    @Query("delete from CartItem c where c.product.id = :productId")
    void deleteByProductId(Long productId);

    List<CartItem> findByUserIdOrderByProductId(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
}
