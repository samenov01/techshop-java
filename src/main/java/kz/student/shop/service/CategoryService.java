package kz.student.shop.service;

import kz.student.shop.dto.CategoryRequest;
import kz.student.shop.dto.CategoryResponse;
import kz.student.shop.exception.ConflictException;
import kz.student.shop.exception.NotFoundException;
import kz.student.shop.model.Category;
import kz.student.shop.repository.CategoryRepository;
import kz.student.shop.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("id")).stream()
                .map(this::toResponse).toList();
    }

    public CategoryResponse findById(Long id) {
        return toResponse(getCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Категория с таким названием уже существует");
        }
        Category category = new Category();
        category.setName(name);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Категория с таким названием уже существует");
        }
        category.setName(name);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = getCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Нельзя удалить категорию, пока в ней есть товары");
        }
        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
