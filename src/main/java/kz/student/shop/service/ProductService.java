package kz.student.shop.service;

import kz.student.shop.dto.ProductRequest;
import kz.student.shop.dto.ProductResponse;
import kz.student.shop.exception.NotFoundException;
import kz.student.shop.model.Category;
import kz.student.shop.model.Product;
import kz.student.shop.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CartItemRepository cartItemRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          CartItemRepository cartItemRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public List<ProductResponse> findAll() {
        return productRepository.findAllByActiveTrue(Sort.by("id")).stream()
                .map(this::toResponse).toList();
    }

    public List<ProductResponse> search(String search, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Проверьте диапазон цен");
        }
        Specification<Product> filter = (root, query, builder) -> builder.isTrue(root.get("active"));
        if (search != null && !search.isBlank()) {
            String term = search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            filter = filter.and((root, query, builder) -> builder.like(builder.lower(root.get("name")), "%" + term + "%", '!'));
        }
        if (categoryId != null) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("category").get("id"), categoryId));
        }
        if (minPrice != null) {
            filter = filter.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            filter = filter.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        Sort order = switch (sort) {
            case "priceAsc" -> Sort.by("price").ascending();
            case "priceDesc" -> Sort.by("price").descending();
            case "nameAsc" -> Sort.by("name").ascending();
            case "nameDesc" -> Sort.by("name").descending();
            case "newest" -> Sort.by("id").descending();
            default -> throw new IllegalArgumentException("Неизвестная сортировка");
        };
        return productRepository.findAll(filter, order.and(Sort.by("id"))).stream().map(this::toResponse).toList();
    }

    public ProductResponse findById(Long id) {
        return toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        fillProduct(product, request);
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.lockById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Товар не найден"));
        fillProduct(product, request);
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.lockById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Товар не найден"));
        product.setActive(false);
        cartItemRepository.deleteByProductId(id);
        productRepository.flush();
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Товар не найден"));
    }

    private void fillProduct(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(category);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStock(), product.getCategory().getId(),
                product.getCategory().getName());
    }
}
