package com.japanese.content.repository;

import com.japanese.content.entity.Category;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    List<Category> findAllByOrderByNameAsc();
}
