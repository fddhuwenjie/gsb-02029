package com.bookstore.controller;

import com.bookstore.constant.BookStatus;
import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.ApiResponse;
import com.bookstore.entity.Book;
import com.bookstore.entity.Category;
import com.bookstore.entity.Order;
import com.bookstore.entity.User;
import com.bookstore.repository.*;
import com.bookstore.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final FavoriteRepository favoriteRepository;
    private final OrderService orderService;

    public AdminController(UserRepository userRepository, BookRepository bookRepository,
                          CategoryRepository categoryRepository, OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository, CartItemRepository cartItemRepository,
                          FavoriteRepository favoriteRepository, OrderService orderService) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartItemRepository = cartItemRepository;
        this.favoriteRepository = favoriteRepository;
        this.orderService = orderService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> getDashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("userCount", userRepository.count());
        data.put("bookCount", bookRepository.countActiveBooks());
        data.put("orderCount", orderRepository.countAllOrders());
        BigDecimal totalSales = orderRepository.getTotalSales();
        data.put("totalSales", totalSales != null ? totalSales : BigDecimal.ZERO);
        return ApiResponse.success(data);
    }

    @GetMapping("/users")
    public ApiResponse<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = userRepository.findAll(pageRequest);
        return ApiResponse.success(users);
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<?> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        user.setStatus(body.get("status"));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ApiResponse.success(user);
    }

    @GetMapping("/books")
    public ApiResponse<?> getBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Book> books = bookRepository.searchBooks(title, author, categoryId, status, pageRequest);
        return ApiResponse.success(books);
    }

    @PostMapping("/books")
    public ApiResponse<?> createBook(@RequestBody Book book) {
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        if (book.getStatus() == null) {
            book.setStatus(BookStatus.ACTIVE);
        }
        bookRepository.save(book);
        return ApiResponse.success(book);
    }

    @PutMapping("/books/{id}")
    public ApiResponse<?> updateBook(@PathVariable Long id, @RequestBody Book book) {
        Book existing = bookRepository.findById(id).orElse(null);
        if (existing == null) {
            return ApiResponse.error(404, "书籍不存在");
        }
        existing.setTitle(book.getTitle());
        existing.setAuthor(book.getAuthor());
        existing.setIsbn(book.getIsbn());
        existing.setPublisher(book.getPublisher());
        existing.setDescription(book.getDescription());
        existing.setCoverImage(book.getCoverImage());
        existing.setOriginalPrice(book.getOriginalPrice());
        existing.setPrice(book.getPrice());
        existing.setQuality(book.getQuality());
        existing.setStock(book.getStock());
        existing.setStatus(book.getStatus());
        existing.setCategoryId(book.getCategoryId());
        existing.setUpdatedAt(LocalDateTime.now());
        bookRepository.save(existing);
        return ApiResponse.success(existing);
    }

    @DeleteMapping("/books/{id}")
    public ApiResponse<?> deleteBook(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElse(null);
        if (book == null) {
            return ApiResponse.error(404, "书籍不存在");
        }
        if (orderItemRepository.existsByBookId(id)) {
            return ApiResponse.error(400, "该书籍存在关联订单，无法删除");
        }
        cartItemRepository.deleteByBookId(id);
        favoriteRepository.deleteByBookId(id);
        bookRepository.delete(book);
        return ApiResponse.success();
    }

    @GetMapping("/categories")
    public ApiResponse<?> getCategories() {
        return ApiResponse.success(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder")));
    }

    @PostMapping("/categories")
    public ApiResponse<?> createCategory(@RequestBody Category category) {
        categoryRepository.save(category);
        return ApiResponse.success(category);
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<?> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        Category existing = categoryRepository.findById(id).orElse(null);
        if (existing == null) {
            return ApiResponse.error(404, "分类不存在");
        }
        existing.setName(category.getName());
        existing.setIcon(category.getIcon());
        existing.setSortOrder(category.getSortOrder());
        existing.setStatus(category.getStatus());
        categoryRepository.save(existing);
        return ApiResponse.success(existing);
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<?> deleteCategory(@PathVariable Long id) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ApiResponse.error(404, "分类不存在");
        }
        if (bookRepository.existsByCategoryId(id)) {
            return ApiResponse.error(400, "该分类下存在书籍，无法删除");
        }
        categoryRepository.delete(category);
        return ApiResponse.success();
    }

    @GetMapping("/orders")
    public ApiResponse<?> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders;
        if (status != null && !status.isEmpty()) {
            orders = orderRepository.findByStatus(status, pageRequest);
        } else {
            orders = orderRepository.findAll(pageRequest);
        }
        return ApiResponse.success(orders);
    }

    @PutMapping("/orders/{id}/status")
    public ApiResponse<?> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String newStatus = body.get("status");
            String trackingNo = body.get("trackingNo");
            Order order = orderService.adminUpdateOrderStatus(id, newStatus, trackingNo);
            return ApiResponse.success(order);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(409, e.getMessage());
        }
    }

    @PutMapping("/orders/{id}/ship")
    public ApiResponse<?> shipOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String trackingNo = body.get("trackingNo");
            Order order = orderService.shipOrder(id, trackingNo);
            return ApiResponse.success(order);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(409, e.getMessage());
        }
    }
}
