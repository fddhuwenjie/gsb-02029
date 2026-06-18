package com.bookstore.config;

import com.bookstore.constant.BookStatus;
import com.bookstore.constant.UserStatus;
import com.bookstore.entity.Book;
import com.bookstore.entity.Category;
import com.bookstore.entity.User;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CategoryRepository;
import com.bookstore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           BookRepository bookRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initUsers();
        initCategories();
        initBooks();
    }

    private void initUsers() {
        if (!userRepository.existsByEmail("admin@bookstore.com")) {
            User admin = new User();
            admin.setEmail("admin@bookstore.com");
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setNickname("管理员");
            admin.setRole("ADMIN");
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
        }

        if (!userRepository.existsByEmail("user@bookstore.com")) {
            User user = new User();
            user.setEmail("user@bookstore.com");
            user.setPassword(passwordEncoder.encode("123456"));
            user.setNickname("测试用户");
            user.setRole("USER");
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        if (!userRepository.existsByEmail("seller@bookstore.com")) {
            User seller = new User();
            seller.setEmail("seller@bookstore.com");
            seller.setPassword(passwordEncoder.encode("123456"));
            seller.setNickname("书籍卖家");
            seller.setRole("USER");
            seller.setStatus(UserStatus.ACTIVE);
            userRepository.save(seller);
        }
    }

    private void initCategories() {
        if (categoryRepository.count() > 0) return;
        log.info("初始化分类数据...");

        String[][] cats = {
                {"文学小说", "book", "1"},
                {"教材教辅", "graduation-cap", "2"},
                {"计算机", "laptop", "3"},
                {"经济管理", "chart-line", "4"},
                {"人文社科", "users", "5"},
                {"外语学习", "globe", "6"},
                {"考试辅导", "edit", "7"},
                {"生活休闲", "coffee", "8"}
        };

        for (String[] c : cats) {
            Category cat = new Category();
            cat.setName(c[0]);
            cat.setIcon(c[1]);
            cat.setSortOrder(Integer.parseInt(c[2]));
            cat.setStatus(1);
            categoryRepository.save(cat);
        }
    }

    private void initBooks() {
        if (bookRepository.count() > 0) return;
        log.info("初始化书籍数据...");

        User seller = userRepository.findByEmail("user@bookstore.com").orElse(null);
        User seller2 = userRepository.findByEmail("seller@bookstore.com").orElse(null);
        if (seller == null || seller2 == null) return;

        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) return;

        Long catLit = findCategoryId(categories, "文学小说");
        Long catEdu = findCategoryId(categories, "教材教辅");
        Long catCS = findCategoryId(categories, "计算机");
        Long catEco = findCategoryId(categories, "经济管理");
        Long catHum = findCategoryId(categories, "人文社科");
        Long catLang = findCategoryId(categories, "外语学习");
        Long catExam = findCategoryId(categories, "考试辅导");
        Long catLife = findCategoryId(categories, "生活休闲");

        List<Book> books = Arrays.asList(
                makeBook("活着", "余华", "9787506365437", "作家出版社",
                        "讲述了农村人福贵悲惨的人生遭遇。福贵本是个阔少爷，可他嗜赌如命，终于赌光了家业，一贫如洗。这是一部充满血泪的小说，一曲关于生命的悲歌。",
                        "/covers/huozhe.jpg", 45.00, 25.00, "九成新", 3, catLit, seller.getId(), 128),
                makeBook("三体", "刘慈欣", "9787536692930", "重庆出版社",
                        "军方探寻外星文明的绝秘计划'红岸工程'取得了突破性进展。在按下发射键的那一刻，历经劫难的叶文洁没有意识到，她彻底改变了人类的命运。",
                        "/covers/santi.jpg", 68.00, 35.00, "八成新", 2, catLit, seller.getId(), 256),
                makeBook("Java编程思想", "Bruce Eckel", "9787111213826", "机械工业出版社",
                        "Java学习经典之作，从基础语法到最高级特性，适合各层次Java程序员阅读。帮助你深入理解Java语言和编程思想。",
                        "/covers/java.jpg", 108.00, 45.00, "七成新", 5, catCS, seller2.getId(), 89),
                makeBook("人类简史", "尤瓦尔·赫拉利", "9787508647357", "中信出版社",
                        "从十万年前有生命迹象开始到21世纪资本、科技交织的人类发展史。理清了影响人类发展的重大脉络，是一部宏大的人类简史。",
                        "/covers/renleijiashi.jpg", 68.00, 30.00, "八成新", 3, catHum, seller2.getId(), 203),
                makeBook("高等数学（第七版）上册", "同济大学", "9787040396638", "高等教育出版社",
                        "经典高数教材，适合理工科学生使用。内容涵盖函数与极限、导数与微分、微分中值定理与导数的应用等核心知识点。",
                        "/covers/gaoshu.jpg", 38.00, 18.00, "八成新", 8, catEdu, seller.getId(), 342),
                makeBook("Python编程从入门到实践", "Eric Matthes", "9787115428028", "人民邮电出版社",
                        "一本针对所有层次Python读者而作的入门书。全书分两部分：基础知识和项目实践，手把手带你从零开始掌握Python编程。",
                        "/covers/python.jpg", 89.00, 42.00, "九成新", 6, catCS, seller2.getId(), 178),
                makeBook("百年孤独", "加西亚·马尔克斯", "9787544253994", "南海出版公司",
                        "魔幻现实主义文学的代表作，描写了布恩迪亚家族七代人的传奇故事，以及加勒比海沿岸小镇马孔多的百年兴衰。",
                        "/covers/huozhe.jpg", 55.00, 28.00, "八成新", 4, catLit, seller2.getId(), 167),
                makeBook("数据结构与算法分析", "Mark Allen Weiss", "9787111521143", "机械工业出版社",
                        "经典计算机科学教材，系统介绍了常用数据结构和算法分析方法，涵盖表、栈、队列、树、散列、排序、图论等核心内容。",
                        "/covers/java.jpg", 79.00, 35.00, "七成新", 3, catCS, seller.getId(), 95),
                makeBook("经济学原理（微观经济学分册）", "曼昆", "9787301150894", "北京大学出版社",
                        "全球最受欢迎的经济学入门教材之一。以浅显易懂的方式阐述经济学基本原理，帮助读者建立经济学思维。",
                        "/covers/renleijiashi.jpg", 72.00, 32.00, "九成新", 5, catEco, seller2.getId(), 186),
                makeBook("新概念英语2：实践与进步", "L.G.Alexander", "9787560013466", "外语教学与研究出版社",
                        "经典英语学习教材，适合有一定英语基础的学习者。通过96篇课文和丰富的练习提升听说读写能力。",
                        "/covers/gaoshu.jpg", 42.00, 20.00, "八成新", 10, catLang, seller.getId(), 278),
                makeBook("考研英语历年真题详解", "张剑", "9787501256789", "世界知识出版社",
                        "涵盖近20年考研英语真题，逐题详细解析，分析命题思路与解题技巧，适合考研备考学生系统复习。",
                        "/covers/python.jpg", 68.00, 30.00, "九成新", 6, catExam, seller.getId(), 412),
                makeBook("小王子", "安托万·德·圣-埃克苏佩里", "9787020042494", "人民文学出版社",
                        "以飞行员作为故事叙述者，讲述了小王子从自己星球出发前往地球的过程中所经历的各种历险。一本关于爱与责任的经典童话。",
                        "/covers/santi.jpg", 32.00, 15.00, "全新", 7, catLife, seller2.getId(), 320)
        );

        bookRepository.saveAll(books);
        log.info("初始化了 {} 本书籍", books.size());
    }

    private Long findCategoryId(List<Category> categories, String name) {
        return categories.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .map(Category::getId)
                .orElse(null);
    }

    private Book makeBook(String title, String author, String isbn, String publisher,
                          String description, String coverImage, double origPrice, double price,
                          String quality, int stock, Long categoryId, Long sellerId, int viewCount) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setIsbn(isbn);
        book.setPublisher(publisher);
        book.setDescription(description);
        book.setCoverImage(coverImage);
        book.setOriginalPrice(BigDecimal.valueOf(origPrice));
        book.setPrice(BigDecimal.valueOf(price));
        book.setQuality(quality);
        book.setStock(stock);
        book.setCategoryId(categoryId);
        book.setSellerId(sellerId);
        book.setStatus(BookStatus.ACTIVE);
        book.setViewCount(viewCount);
        return book;
    }
}
