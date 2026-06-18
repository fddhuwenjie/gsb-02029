package com.bookstore.controller;

import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.BookRequest;
import com.bookstore.entity.Book;
import com.bookstore.service.BookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
public class BookController {
    
    private final BookService bookService;
    
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }
    
    @GetMapping
    public ApiResponse<?> getBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        
        Page<Book> books = bookService.getBooks(page, size, categoryId, keyword);
        return ApiResponse.success(books);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<?> getBook(@PathVariable Long id) {
        Book book = bookService.getBookById(id);
        if (book == null) {
            return ApiResponse.error(404, "书籍不存在");
        }
        bookService.increaseViewCount(id);
        return ApiResponse.success(book);
    }
    
    @PostMapping
    public ApiResponse<?> createBook(
            @Valid @RequestBody BookRequest request,
            @AuthenticationPrincipal Long userId) {
        Book created = bookService.createBook(request, userId);
        return ApiResponse.success(created);
    }
    
    @GetMapping("/my")
    public ApiResponse<?> getMyBooks(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Book> books = bookService.getMyBooks(userId, page, size);
        return ApiResponse.success(books);
    }
}
