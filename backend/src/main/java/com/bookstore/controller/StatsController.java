package com.bookstore.controller;

import com.bookstore.dto.ApiResponse;
import com.bookstore.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    
    private final StatsService statsService;
    
    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }
    
    @GetMapping
    public ApiResponse<?> getStats() {
        Map<String, Object> stats = statsService.getStats();
        return ApiResponse.success(stats);
    }
}
