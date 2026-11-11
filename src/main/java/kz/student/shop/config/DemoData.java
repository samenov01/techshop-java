package kz.student.shop.config;

import kz.student.shop.model.*;
import kz.student.shop.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final PasswordEncoder encoder;

    public DemoData(UserRepository users, CategoryRepository categories, ProductRepository products, PasswordEncoder encoder) {
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        addUser("Администратор", "admin@techshop.local", "DemoAdmin123!", "ADMIN");
        addUser("Покупатель", "customer@techshop.local", "DemoUser123!", "CUSTOMER");
        if (products.count() == 0 && categories.count() == 0) {
            Category mice = category("Мыши");
            Category keyboards = category("Клавиатуры");
            Category headphones = category("Наушники");
            product(mice, "Мышь M1", "Лёгкая проводная мышь. Точное управление и удобная форма на каждый день.", "5000", 8);
            product(mice, "Мышь Air", "Беспроводная мышь с тихими кнопками. Для работы дома и в офисе.", "8500", 12);
            product(mice, "Мышь Pro", "Эргономичная форма и регулируемая чувствительность.", "14500", 4);
            product(keyboards, "Клавиатура K1", "Компактная проводная клавиатура. Всё необходимое на вашем столе.", "12000", 5);
            product(keyboards, "Клавиатура Compact", "Механические клавиши и компактный корпус без цифрового блока.", "24000", 7);
            product(keyboards, "Клавиатура Studio", "Полноразмерная клавиатура с мягким и тихим ходом клавиш.", "18500", 6);
            product(headphones, "Наушники H1", "Проводные наушники с микрофоном для звонков и музыки.", "9000", 0);
            product(headphones, "Наушники Wave", "Беспроводные наушники с мягкими амбушюрами.", "22000", 9);
            product(headphones, "Наушники Focus", "Закрытые наушники для сосредоточенной работы и любимой музыки.", "16500", 3);
        }
    }

    private void addUser(String name, String email, String password, String role) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        users.save(user);
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return categories.save(category);
    }

    private void product(Category category, String name, String description, String price, int stock) {
        Product product = new Product();
        product.setCategory(category);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        products.save(product);
    }
}
