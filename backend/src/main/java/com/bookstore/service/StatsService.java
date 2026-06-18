package com.bookstore.service;

import com.bookstore.repository.BookRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class StatsService {
    
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    
    public StatsService(BookRepository bookRepository, UserRepository userRepository, 
                       OrderRepository orderRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("bookCount", bookRepository.countActiveBooks());
        stats.put("userCount", userRepository.count());
        stats.put("orderCount", orderRepository.countAllOrders());
        return stats;
    }
}
