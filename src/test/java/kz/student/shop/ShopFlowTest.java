package kz.student.shop;

import kz.student.shop.dto.*;
import kz.student.shop.config.DemoData;
import kz.student.shop.exception.ConflictException;
import kz.student.shop.model.*;
import kz.student.shop.repository.*;
import kz.student.shop.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired UserService userService;
    @Autowired CartService cart;
    @Autowired OrderService orderService;
    @Autowired StatisticsService statistics;
    @Autowired ProductService productService;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    private Long productId;
    private Long categoryId;

    @BeforeEach
    void setup() {
        clean();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String email : List.of("one@test.local", "two@test.local")) {
                User user = new User();
                user.setName(email);
                user.setEmail(email);
                user.setPasswordHash(encoder.encode("Password123!"));
                user.setRole("CUSTOMER");
                users.save(user);
            }
            Category category = new Category();
            category.setName("Мыши");
            categoryId = categories.save(category).getId();
            Product product = new Product();
            product.setName("Последняя мышь");
            product.setDescription("Тестовый товар");
            product.setPrice(new BigDecimal("1250.50"));
            product.setStock(1);
            product.setCategory(category);
            productId = products.save(product).getId();
        });
    }

    @AfterEach
    void clean() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orderItems.deleteAllInBatch();
            cartItems.deleteAllInBatch();
            orders.deleteAllInBatch();
            products.deleteAllInBatch();
            categories.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @Test
    void registrationAlwaysCreatesCustomerAndHashesPassword() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"email\":\"new@test.local\",\"password\":\"Password123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        User user = users.findByEmailIgnoreCase("new@test.local").orElseThrow();
        assertThat(encoder.matches("Password123!", user.getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "new@test.local").param("password", "Password123!"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "new@test.local").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejected() {
        assertThatThrownBy(() -> userService.register(new RegisterRequest("Test", "ONE@test.local", "Password123!")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void protectsAdminAndPrivateEndpoints() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").with(user("one@test.local").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/{id}", productId).with(user("one@test.local").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cart").with(user("one@test.local")).contentType(APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cartBelongsOnlyToItsOwner() throws Exception {
        cart.add("one@test.local", new CartRequest(productId, 1));
        mvc.perform(get("/api/cart").with(user("two@test.local")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(delete("/api/cart/{id}", productId).with(user("two@test.local")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(cart.get("one@test.local").count()).isEqualTo(1);
    }

    @Test
    void checkoutUsesCurrentPriceAndClearsCart() {
        cart.add("one@test.local", new CartRequest(productId, 1));
        productService.update(productId, new ProductRequest("Мышь", "Описание", new BigDecimal("1500.75"), 1, categoryId));
        var order = orderService.checkout("one@test.local");
        assertThat(order.totalAmount()).isEqualByComparingTo("1500.75");
        assertThat(order.items()).hasSize(1);
        assertThat(products.findById(productId).orElseThrow().getStock()).isZero();
        assertThat(cart.get("one@test.local").items()).isEmpty();
        productService.update(productId, new ProductRequest("Другое название", "Описание", new BigDecimal("2000"), 0, categoryId));
        assertThat(orderService.history("one@test.local").get(0).items().get(0).name()).isEqualTo("Мышь");
    }

    @Test
    void emptyCartCannotBeOrdered() {
        assertThatThrownBy(() -> orderService.checkout("one@test.local")).isInstanceOf(ConflictException.class);
        assertThat(orders.count()).isZero();
    }

    @Test
    void insufficientStockRollsBackEntireOrder() {
        Long secondId = new TransactionTemplate(transactionManager).execute(status -> {
            Product second = new Product();
            second.setName("Второй товар"); second.setDescription("Описание");
            second.setCategory(categories.findById(categoryId).orElseThrow());
            second.setPrice(BigDecimal.TEN); second.setStock(1);
            return products.save(second).getId();
        });
        cart.add("one@test.local", new CartRequest(productId, 1));
        cart.add("one@test.local", new CartRequest(secondId, 1));
        productService.update(secondId, new ProductRequest("Второй товар", "Описание", BigDecimal.TEN, 0, categoryId));
        assertThatThrownBy(() -> orderService.checkout("one@test.local")).isInstanceOf(ConflictException.class);
        assertThat(products.findById(productId).orElseThrow().getStock()).isEqualTo(1);
        assertThat(orders.count()).isZero();
        assertThat(orderItems.count()).isZero();
        assertThat(cart.get("one@test.local").count()).isEqualTo(2);
    }

    @Test
    void concurrentOrdersCannotOversellLastProduct() throws Exception {
        cart.add("one@test.local", new CartRequest(productId, 1));
        cart.add("two@test.local", new CartRequest(productId, 1));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = List.of("one@test.local", "two@test.local").stream().map(email -> pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try { orderService.checkout(email); return true; }
                catch (ConflictException exception) { return false; }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int success = 0;
            for (Future<Boolean> future : futures) if (future.get(15, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
            assertThat(orders.count()).isEqualTo(1);
            assertThat(products.findById(productId).orElseThrow().getStock()).isZero();
            assertThat(cartItems.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void cancellationRestocksOnlyOnceAndRefundsPayment() {
        cart.add("one@test.local", new CartRequest(productId, 1));
        var order = orderService.checkout("one@test.local");
        orderService.pay("one@test.local", order.id());
        orderService.pay("one@test.local", order.id());
        orderService.cancel("one@test.local", order.id());
        var cancelled = orderService.cancel("one@test.local", order.id());
        assertThat(cancelled.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(products.findById(productId).orElseThrow().getStock()).isEqualTo(1);
        assertThatThrownBy(() -> orderService.pay("one@test.local", order.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotPayOrCancelAnotherUsersOrder() throws Exception {
        cart.add("one@test.local", new CartRequest(productId, 1));
        var order = orderService.checkout("one@test.local");
        mvc.perform(post("/api/orders/{id}/pay", order.id()).with(user("two@test.local")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/cancel", order.id()).with(user("two@test.local")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").with(user("two@test.local")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void statusTransitionsAndStatisticsUseCompletedPaidOrders() {
        cart.add("one@test.local", new CartRequest(productId, 1));
        var order = orderService.checkout("one@test.local");
        assertThatThrownBy(() -> orderService.changeStatus(order.id(), "COMPLETED")).isInstanceOf(ConflictException.class);
        orderService.changeStatus(order.id(), "CONFIRMED");
        assertThatThrownBy(() -> orderService.changeStatus(order.id(), "SHIPPED")).isInstanceOf(ConflictException.class);
        orderService.pay("one@test.local", order.id());
        orderService.changeStatus(order.id(), "SHIPPED");
        assertThatThrownBy(() -> orderService.cancel("one@test.local", order.id())).isInstanceOf(ConflictException.class);
        orderService.changeStatus(order.id(), "COMPLETED");
        assertThat(statistics.get().revenue()).isEqualByComparingTo("1250.50");
        assertThat(statistics.get().orderCount()).isEqualTo(1);
    }

    @Test
    void catalogUpgradePreservesStockAndOrderSnapshots() {
        productService.update(productId, new ProductRequest("Мышь M1", "Старое описание", new BigDecimal("1250.50"), 1, categoryId));
        cart.add("one@test.local", new CartRequest(productId, 1));
        var order = orderService.checkout("one@test.local");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                new DemoData(users, categories, products, encoder).run(null));
        Product upgraded = products.findByName("Logitech G PRO X SUPERLIGHT 2").orElseThrow();
        assertThat(upgraded.getId()).isEqualTo(productId);
        assertThat(upgraded.getStock()).isZero();
        var savedOrder = orderService.history("one@test.local").get(0);
        assertThat(savedOrder.id()).isEqualTo(order.id());
        assertThat(savedOrder.totalAmount()).isEqualByComparingTo("1250.50");
        assertThat(savedOrder.items().get(0).name()).isEqualTo("Мышь M1");
    }

    @Test
    void catalogRestartDoesNotResetEditedProducts() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                new DemoData(users, categories, products, encoder).run(null));
        Product product = products.findByName("VXE Dragonfly R1 Pro").orElseThrow();
        productService.update(product.getId(), new ProductRequest(product.getName(), "Новое описание", new BigDecimal("19990.00"), 2, categoryId));
        long count = products.count();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                new DemoData(users, categories, products, encoder).run(null));
        Product saved = products.findById(product.getId()).orElseThrow();
        assertThat(products.count()).isEqualTo(count);
        assertThat(saved.getStock()).isEqualTo(2);
        assertThat(saved.getPrice()).isEqualByComparingTo("19990.00");
    }

    @Test
    void searchesAndFiltersCatalog() throws Exception {
        mvc.perform(get("/api/products").param("search", "последняя").param("categoryId", categoryId.toString())
                        .param("minPrice", "1000").param("maxPrice", "2000").param("sort", "priceAsc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/products").param("minPrice", "2000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/products").param("minPrice", "2000").param("maxPrice", "1000"))
                .andExpect(status().isBadRequest());
    }
}
