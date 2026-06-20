package com.bookstore.repository;

import com.bookstore.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {
    Page<Book> findByStatus(Integer status, Pageable pageable);
    Page<Book> findByCategoryIdAndStatus(Long categoryId, Integer status, Pageable pageable);
    Page<Book> findBySellerIdAndStatus(Long sellerId, Integer status, Pageable pageable);
    
    @Query("SELECT b FROM Book b WHERE b.status = :status AND (b.title LIKE %:keyword% OR b.author LIKE %:keyword%)")
    Page<Book> searchByKeyword(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);
    
    @Query("SELECT b FROM Book b WHERE " +
           "(:title IS NULL OR b.title LIKE %:title%) AND " +
           "(:author IS NULL OR b.author LIKE %:author%) AND " +
           "(:categoryId IS NULL OR b.categoryId = :categoryId) AND " +
           "(:status IS NULL OR b.status = :status)")
    Page<Book> searchBooks(@Param("title") String title, 
                          @Param("author") String author,
                          @Param("categoryId") Long categoryId,
                          @Param("status") Integer status, 
                          Pageable pageable);
    
    @Query("SELECT COUNT(b) FROM Book b WHERE b.status = 1")
    Long countActiveBooks();

    boolean existsByCategoryId(Long categoryId);

    /**
     * 原子扣减库存：仅在 stock >= quantity 时才会成功，返回受影响行数。
     * 通过 SQL 层 WHERE 条件保证并发安全，避免读-改-写竞态。
     */
    @Modifying
    @Query("UPDATE Book b SET b.stock = b.stock - :quantity WHERE b.id = :bookId AND b.stock >= :quantity")
    int decrementStock(@Param("bookId") Long bookId, @Param("quantity") int quantity);

    /**
     * 释放库存：取消/超时关闭订单时归还预占的库存，无条件累加。
     */
    @Modifying
    @Query("UPDATE Book b SET b.stock = b.stock + :quantity WHERE b.id = :bookId")
    int incrementStock(@Param("bookId") Long bookId, @Param("quantity") int quantity);
}
