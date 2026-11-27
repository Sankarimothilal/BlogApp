package com.blogsphere.repository;

import com.blogsphere.entity.Notification;
import com.blogsphere.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);
    List<Notification> findTop5ByRecipientOrderByCreatedAtDesc(User recipient);
    List<Notification> findByRecipientAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            User recipient, LocalDateTime startInclusive, LocalDateTime endExclusive);
    long countByRecipientAndIsReadFalse(User recipient);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.targetUrl = :targetUrl")
    void deleteByTargetUrl(@Param("targetUrl") String targetUrl);
}
