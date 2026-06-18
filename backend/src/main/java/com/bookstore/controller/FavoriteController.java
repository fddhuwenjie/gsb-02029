package com.bookstore.controller;

import com.bookstore.dto.ApiResponse;
import com.bookstore.entity.Favorite;
import com.bookstore.repository.FavoriteRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    
    private final FavoriteRepository favoriteRepository;
    
    public FavoriteController(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }
    
    @GetMapping
    public ApiResponse<?> getFavorites(@AuthenticationPrincipal Long userId) {
        List<Favorite> favorites = favoriteRepository.findByUserId(userId);
        return ApiResponse.success(favorites);
    }
    
    @PostMapping("/{bookId}")
    public ApiResponse<?> addFavorite(@PathVariable Long bookId, @AuthenticationPrincipal Long userId) {
        if (favoriteRepository.existsByUserIdAndBookId(userId, bookId)) {
            return ApiResponse.error(400, "已收藏");
        }
        
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setBookId(bookId);
        favoriteRepository.save(favorite);
        return ApiResponse.success(favorite);
    }
    
    @DeleteMapping("/{bookId}")
    public ApiResponse<?> removeFavorite(@PathVariable Long bookId, @AuthenticationPrincipal Long userId) {
        Favorite favorite = favoriteRepository.findByUserIdAndBookId(userId, bookId).orElse(null);
        if (favorite != null) {
            favoriteRepository.delete(favorite);
        }
        return ApiResponse.success();
    }
    
    @GetMapping("/check/{bookId}")
    public ApiResponse<?> checkFavorite(@PathVariable Long bookId, @AuthenticationPrincipal Long userId) {
        boolean isFavorite = favoriteRepository.existsByUserIdAndBookId(userId, bookId);
        return ApiResponse.success(isFavorite);
    }
}
