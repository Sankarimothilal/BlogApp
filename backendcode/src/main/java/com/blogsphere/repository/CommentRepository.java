package com.blogsphere.repository;

import com.blogsphere.entity.Comment;
import com.blogsphere.entity.Post;
import com.blogsphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtDesc(Post post);
    List<Comment> findByPostAndParentIsNullOrderByCreatedAtDesc(Post post);
    List<Comment> findAllByOrderByCreatedAtDesc();
    long countByPost(Post post);
    long countByUser(User user);

    @Query("SELECT parent.id FROM Comment parent WHERE parent.post.id = :postId")
    List<Long> findIdsByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Comment child SET child.parent = null WHERE child.parent.id IN :parentIds")
    void detachRepliesToComments(@Param("parentIds") List<Long> parentIds);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteForPost(@Param("postId") Long postId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.author = :author AND c.post.status = 'PUBLISHED'")
    long countCommentsReceivedByAuthor(@Param("author") User author);
}
