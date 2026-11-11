package kz.student.shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.student.shop.model.*;
import kz.student.shop.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "ADMIN")
class CatalogTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private ProductRepository products;
    @Autowired
    private UserRepository users;
    @Autowired
    private CartItemRepository cartItems;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderItemRepository orderItems;

    @Test
    void categoryCrud() throws Exception {
        String result = mvc.perform(post("/api/categories")
                        .with(csrf()).contentType(APPLICATION_JSON).content("{\"name\":\"  Тестовая категория  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Тестовая категория"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(result).get("id").asLong();

        mvc.perform(get("/api/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Тестовая категория"));
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Тестовая категория")));
        mvc.perform(put("/api/categories/{id}", id).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Другая категория\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Другая категория"));
        assertThat(categories.findById(id).orElseThrow().getName()).isEqualTo("Другая категория");
        mvc.perform(delete("/api/categories/{id}", id).with(csrf())).andExpect(status().isNoContent());
        assertThat(categories.existsById(id)).isFalse();
    }

    @Test
    void productCrud() throws Exception {
        Category category = createCategory("Тестовые товары");
        String result = mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(category.getId(), "2500.50", 3)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.categoryName").value(category.getName()))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(result).get("id").asLong();

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(2500.50));
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].name", hasItem("Тестовый товар")));
        Category secondCategory = createCategory("Новая категория");
        mvc.perform(put("/api/products/{id}", id).with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(secondCategory.getId(), "3000.00", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.categoryId").value(secondCategory.getId()));
        Product saved = products.findById(id).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo("3000.00");
        assertThat(saved.getCategory().getId()).isEqualTo(secondCategory.getId());
        mvc.perform(delete("/api/products/{id}", id).with(csrf())).andExpect(status().isNoContent());
        assertThat(products.existsById(id)).isFalse();
    }

    @Test
    void rejectsEmptyCategoryName() throws Exception {
        mvc.perform(post("/api/categories").with(csrf()).contentType(APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void rejectsDuplicateCategory() throws Exception {
        createCategory("Повторная категория");
        mvc.perform(post("/api/categories").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"повторная категория\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRenamingCategoryToExistingName() throws Exception {
        createCategory("Первая категория");
        Category second = createCategory("Вторая категория");
        mvc.perform(put("/api/categories/{id}", second.getId()).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Первая категория\"}"))
                .andExpect(status().isConflict());
        assertThat(categories.findById(second.getId()).orElseThrow().getName()).isEqualTo("Вторая категория");
    }

    @Test
    void rejectsNegativeStockAndPrice() throws Exception {
        Category category = createCategory("Проверка чисел");
        long count = products.count();
        mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(category.getId(), "-10.00", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.stock").exists());
        assertThat(products.count()).isEqualTo(count);
    }

    @Test
    void rejectsFractionalStock() throws Exception {
        Category category = createCategory("Проверка остатка");
        mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(category.getId(), "100.00", 1.5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPriceWithThreeDecimalPlaces() throws Exception {
        Category category = createCategory("Проверка цены");
        mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(category.getId(), "100.001", 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    void rejectsProductWithMissingCategory() throws Exception {
        mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(Long.MAX_VALUE, "100.00", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidUpdateKeepsProductUnchanged() throws Exception {
        Product product = createProduct();
        mvc.perform(put("/api/products/{id}", product.getId()).with(csrf()).contentType(APPLICATION_JSON)
                        .content(productJson(Long.MAX_VALUE, "999.00", 7)))
                .andExpect(status().isNotFound());
        Product saved = products.findById(product.getId()).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo("5000.00");
        assertThat(saved.getStock()).isEqualTo(8);
    }

    @Test
    void cannotDeleteCategoryWithProducts() throws Exception {
        Product product = createProduct();
        mvc.perform(delete("/api/categories/{id}", product.getCategory().getId()).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(categories.existsById(product.getCategory().getId())).isTrue();
        assertThat(products.existsById(product.getId())).isTrue();
    }

    @Test
    void cannotDeleteProductInCart() throws Exception {
        Product product = createProduct();
        CartItem item = new CartItem();
        item.setUser(createUser());
        item.setProduct(product);
        item.setQuantity(1);
        cartItems.saveAndFlush(item);

        mvc.perform(delete("/api/products/{id}", product.getId()).with(csrf())).andExpect(status().isConflict());
        assertThat(products.existsById(product.getId())).isTrue();
        assertThat(cartItems.existsById(item.getId())).isTrue();
    }

    @Test
    void cannotDeleteProductInOrder() throws Exception {
        Product product = createProduct();
        Order order = new Order();
        order.setUser(createUser());
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("NEW");
        order.setPaymentStatus("UNPAID");
        order.setTotalAmount(product.getPrice());
        orders.saveAndFlush(order);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(product.getPrice());
        orderItems.saveAndFlush(item);

        mvc.perform(delete("/api/products/{id}", product.getId()).with(csrf())).andExpect(status().isConflict());
        assertThat(products.existsById(product.getId())).isTrue();
        assertThat(orderItems.existsById(item.getId())).isTrue();
    }

    @Test
    void missingRecordsReturn404() throws Exception {
        mvc.perform(get("/api/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/{id}", Long.MAX_VALUE).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/categories/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/categories/{id}", Long.MAX_VALUE).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void malformedRequestsReturn400() throws Exception {
        mvc.perform(get("/api/products/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.categoryId").exists());
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categories.saveAndFlush(category);
    }

    private Product createProduct() {
        Product product = new Product();
        product.setCategory(createCategory("Категория для связи"));
        product.setName("Мышь M1");
        product.setDescription("Проводная мышь");
        product.setPrice(new BigDecimal("5000.00"));
        product.setStock(8);
        return products.saveAndFlush(product);
    }

    private User createUser() {
        User user = new User();
        user.setName("Тестовый покупатель");
        user.setEmail("test@example.invalid");
        user.setPasswordHash("unused-test-value");
        user.setRole("CUSTOMER");
        return users.saveAndFlush(user);
    }

    private String productJson(Long categoryId, String price, Number stock) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "name", "Тестовый товар", "description", "Описание тестового товара",
                "price", new BigDecimal(price), "stock", stock, "categoryId", categoryId));
    }
}
