package com.blogsphere.repository;

import com.blogsphere.entity.Bookmark;
import com.blogsphere.entity.Post;
import com.blogsphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByUserAndPost(User user, Post post);
    boolean existsByUserAndPost(User user, Post post);
    List<Bookmark> findByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);

    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.post.id = :postId")
    void deleteForPost(@Param("postId") Long postId);

    @Query("SELECT COUNT(b) FROM Bookmark b WHERE b.post.author = :author AND b.post.status = 'PUBLISHED'")
    long countBookmarksReceivedByAuthor(@Param("author") User author);
}
