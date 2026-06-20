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

    @Modifying
    @Query("UPDATE Book b SET b.stock = b.stock - :quantity, b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :bookId AND b.stock >= :quantity AND b.status = 1")
    int decrementStock(@Param("bookId") Long bookId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Book b SET b.stock = b.stock + :quantity, b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :bookId")
    int incrementStock(@Param("bookId") Long bookId, @Param("quantity") int quantity);
}
