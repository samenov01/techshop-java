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
        Category mice = category("Мыши");
        Category keyboards = category("Клавиатуры");
        Category headphones = category("Наушники");
        product(mice, "Мышь M1", "Logitech G PRO X SUPERLIGHT 2",
                "Беспроводная игровая мышь с сенсором HERO 2, переключателями LIGHTFORCE и подключением LIGHTSPEED. Вес — 60 г.", "79990", 8);
        product(mice, "Мышь Air", "VXE Dragonfly R1 Pro",
                "Лёгкая беспроводная мышь с сенсором PAW3395. Подключение по радиоканалу 2,4 ГГц и USB, вес — 48 г.", "24990", 12);
        product(mice, "Мышь Pro", "VGN Dragonfly F1 Pro",
                "Игровая мышь с сенсором PAW3395 и контроллером Nordic. Радиоканал 2,4 ГГц и проводное подключение.", "27990", 4);
        product(keyboards, "Клавиатура K1", "Logitech G515 LIGHTSPEED TKL",
                "Низкопрофильная механическая клавиатура без цифрового блока. LIGHTSPEED, Bluetooth и USB, подсветка LIGHTSYNC RGB.", "74990", 5);
        product(keyboards, "Клавиатура Compact", "VGN V87 V2",
                "Механическая TKL-клавиатура с горячей заменой переключателей, RGB и gasket-конструкцией. USB, Bluetooth и 2,4 ГГц.", "29990", 7);
        product(keyboards, "Клавиатура Studio", "VGN S99",
                "Механическая клавиатура формата 98% с цифровым блоком. Горячая замена переключателей, RGB и три способа подключения.", "39990", 6);
        product(headphones, "Наушники H1", "Logitech G PRO X 2 LIGHTSPEED",
                "Беспроводная гарнитура с 50-мм графеновыми динамиками и съёмным микрофоном. LIGHTSPEED, Bluetooth и разъём 3,5 мм.", "119990", 5);
        product(headphones, "Наушники Wave", "HyperX Cloud III",
                "Проводная игровая гарнитура с 53-мм динамиками и съёмным микрофоном. Подключение через 3,5 мм, USB-C и USB-A.", "49990", 9);
        product(headphones, "Наушники Focus", "Logitech G435 LIGHTSPEED",
                "Лёгкая беспроводная гарнитура весом 165 г. Подключение LIGHTSPEED и Bluetooth, встроенные направленные микрофоны.", "32990", 3);
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
        return categories.findByNameIgnoreCase(name).orElseGet(() -> {
            Category category = new Category();
            category.setName(name);
            return categories.save(category);
        });
    }

    private void product(Category category, String oldName, String name, String description, String price, int stock) {
        if (products.existsByName(name)) {
            return;
        }
        Product product = products.findByName(oldName).orElse(null);
        if (product == null) {
            product = new Product();
            product.setStock(stock);
        }
        product.setCategory(category);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        products.save(product);
    }
}
