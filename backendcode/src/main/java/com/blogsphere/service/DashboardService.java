package com.blogsphere.service;

import com.blogsphere.dto.request.ChangePasswordRequest;
import com.blogsphere.dto.request.UpdateProfileRequest;
import com.blogsphere.dto.response.*;
import com.blogsphere.entity.*;
import com.blogsphere.exception.ResourceNotFoundException;
import com.blogsphere.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class DashboardService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserDashboardStatsDTO getUserDashboard(User user) {
        long published = postRepository.countByAuthorAndStatus(user, "PUBLISHED");
        long drafts = postRepository.countByAuthorAndStatus(user, "DRAFT");
        Long totalViews = postRepository.sumPublishedViewsByAuthor(user);
        if (totalViews == null) totalViews = 0L;
        long totalLikes = likeRepository.countLikesReceivedByAuthor(user);
        long totalComments = commentRepository.countCommentsReceivedByAuthor(user);
        long totalBookmarks = bookmarkRepository.countBookmarksReceivedByAuthor(user);

        UserDashboardStatsDTO dto = new UserDashboardStatsDTO(published, drafts, totalViews, totalLikes, new ArrayList<>());
        dto.setTotalComments(totalComments);
        dto.setTotalBookmarks(totalBookmarks);
        double rate = totalViews > 0 ? ((double) (totalLikes + totalComments) / totalViews) * 100 : 0.0;
        dto.setEngagementRate(Math.round(rate * 10.0) / 10.0);
        dto.setAvgViewsPerArticle(published > 0 ? totalViews / published : 0);

        postRepository.findTopByAuthorAndStatusOrderByViewCountDesc(user, "PUBLISHED")
                .ifPresent(topPost -> {
                    dto.setTopPerformingBlogTitle(topPost.getTitle());
                    dto.setTopPerformingBlogViews(topPost.getViewCount() == null ? 0L : topPost.getViewCount());
                });

        List<Notification> recentNotifications = notificationRepository.findTop5ByRecipientOrderByCreatedAtDesc(user);
        DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
        List<UserDashboardStatsDTO.ActivityItemDTO> activity = recentNotifications.stream()
                .map(notification -> new UserDashboardStatsDTO.ActivityItemDTO(
                        notification.getType(),
                        notification.getMessage(),
                        notification.getCreatedAt().format(timestampFormat)))
                .collect(Collectors.toList());
        dto.setRecentActivity(activity);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate firstDay = today.minusDays(6);
        LocalDateTime rangeStart = firstDay.atStartOfDay();
        LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();
        List<Notification> weeklyNotifications = notificationRepository
                .findByRecipientAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        user, rangeStart, rangeEnd);
        dto.setWeeklyActivity(buildWeeklyActivity(weeklyNotifications, firstDay));

        return dto;
    }

    static Map<String, Long> buildWeeklyActivity(List<Notification> notifications, LocalDate firstDay) {
        Map<LocalDate, Long> countsByDate = new java.util.TreeMap<>();
        for (int day = 0; day < 7; day++) {
            countsByDate.put(firstDay.plusDays(day), 0L);
        }
        notifications.forEach(notification -> {
            LocalDate date = notification.getCreatedAt().toLocalDate();
            if (countsByDate.containsKey(date)) {
                countsByDate.merge(date, 1L, Long::sum);
            }
        });

        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        Map<String, Long> weeklyActivity = new LinkedHashMap<>();
        countsByDate.forEach((date, count) -> weeklyActivity.put(date.format(dayFormat), count));
        return weeklyActivity;
    }

    public ReaderDashboardDTO getReaderDashboard(User user) {
        long readCount = readingHistoryRepository.countByUser(user);
        long bookmarkCount = bookmarkRepository.countByUser(user);
        long followingCount = followRepository.countByFollower(user);
        long commentsCount = commentRepository.countByUser(user);

        List<PostSummaryDTO> saved = bookmarkRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(Bookmark::getPost)
                .map(postService::mapToSummaryDTO)
                .collect(Collectors.toList());

        List<ReadingHistory> readingRecords = readingHistoryRepository.findByUserOrderByViewedAtDesc(user);
        List<PostSummaryDTO> history = readingRecords.stream()
                .map(ReadingHistory::getPost)
                .map(postService::mapToSummaryDTO)
                .collect(Collectors.toList());

        List<UserSummaryDTO> followedAuthors = followRepository.findByFollower(user).stream()
                .map(Follow::getFollowing)
                .map(author -> mapToUserSummary(author, user))
                .collect(Collectors.toList());

        ReaderDashboardDTO dto = new ReaderDashboardDTO(
                readCount, bookmarkCount, followingCount, commentsCount, saved, history, followedAuthors);

        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        Map<LocalDate, Long> activityByDate = new java.util.TreeMap<>();
        readingRecords.stream()
                .filter(record -> record.getViewedAt().isAfter(weekStart))
                .forEach(record -> activityByDate.merge(record.getViewedAt().toLocalDate(), 1L, Long::sum));
        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("MMM d");
        Map<String, Long> weeklyActivity = new LinkedHashMap<>();
        activityByDate.forEach((date, count) -> weeklyActivity.put(date.format(dayFormat), count));
        dto.setWeeklyReadingActivity(weeklyActivity);

        Map<String, Long> readingByCategory = new java.util.TreeMap<>();
        readingRecords.forEach(record -> {
            Post post = record.getPost();
            String categoryName = post.getCategory() != null ? post.getCategory().getName() : "General";
            readingByCategory.merge(categoryName, 1L, Long::sum);
        });
        dto.setReadingByCategory(readingByCategory);

        return dto;
    }

    public List<UserSummaryDTO> getAllAuthors(User currentUser) {
        List<User> authors = userRepository.findByRole(Role.ROLE_AUTHOR);
        return authors.stream().map(a -> mapToUserSummary(a, currentUser)).collect(Collectors.toList());
    }

    public List<UserSummaryDTO> getAllUserSummaries(User currentUser) {
        return userRepository.findAll().stream()
                .map(u -> mapToUserSummary(u, currentUser))
                .collect(Collectors.toList());
    }

    public List<CommentDTO> getAllCommentsForAdmin() {
        return commentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> new CommentDTO(
                    c.getId(),
                    c.getPost().getId(),
                    c.getUser().getId(),
                    c.getUser().getFullName(),
                    c.getUser().getAvatarUrl(),
                    c.getContent(),
                    c.getParent() != null ? c.getParent().getId() : null,
                    c.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Long commentId) {
        commentRepository.deleteById(commentId);
    }

    @Transactional
    public void updateUserRole(Long userId, String roleStr) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Role newRole = Role.valueOf(roleStr);
        u.setRole(newRole);
        userRepository.save(u);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Transactional
    public User updateProfile(User user, UpdateProfileRequest request) {
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password does not match");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public AdminStatsDTO getAdminDashboard() {
        long totalUsers = userRepository.count();
        long totalBlogs = postRepository.count();
        long reportedCount = reportRepository.count();
        long pendingReportsCount = reportRepository.countByStatus("PENDING");

        List<Report> reports = reportRepository.findAllByOrderByCreatedAtDesc();
        List<AdminStatsDTO.ReportItemDTO> reportDTOs = reports.stream().limit(10).map(r -> new AdminStatsDTO.ReportItemDTO(
            r.getId(),
            r.getPost().getId(),
            r.getPost().getTitle(),
            r.getReportedBy().getFullName(),
            r.getReason(),
            r.getStatus()
        )).collect(Collectors.toList());

        return new AdminStatsDTO(totalUsers, totalBlogs, reportedCount, pendingReportsCount, reportDTOs);
    }

    public AuthorProfileDTO getAuthorProfile(User author, User currentUser) {
        long totalBlogs = postRepository.countByAuthorAndStatus(author, "PUBLISHED");
        long followersCount = followRepository.countByFollowing(author);
        long followingCount = followRepository.countByFollower(author);

        boolean followedByCurrentUser = false;
        if (currentUser != null && !currentUser.getId().equals(author.getId())) {
            followedByCurrentUser = followRepository.existsByFollowerAndFollowing(currentUser, author);
        }

        AuthorProfileDTO dto = new AuthorProfileDTO();
        dto.setId(author.getId());
        dto.setFullName(author.getFullName());
        dto.setRole(author.getRole().name().replace("ROLE_", ""));
        dto.setBio(author.getBio() != null ? author.getBio() : "Author at BlogSphere.");
        dto.setAvatarUrl(author.getAvatarUrl());
        dto.setTotalBlogs(totalBlogs);
        dto.setFollowersCount(followersCount);
        dto.setFollowingCount(followingCount);
        dto.setFollowedByCurrentUser(followedByCurrentUser);
        return dto;
    }

    public UserSummaryDTO mapToUserSummary(User u, User currentUser) {
        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(u.getId());
        dto.setFullName(u.getFullName());
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole().name());
        dto.setBio(u.getBio());
        dto.setAvatarUrl(u.getAvatarUrl());
        dto.setTotalBlogs(postRepository.countByAuthorAndStatus(u, "PUBLISHED"));
        dto.setFollowersCount(followRepository.countByFollowing(u));
        dto.setCreatedAt(u.getCreatedAt());

        if (currentUser != null && !currentUser.getId().equals(u.getId())) {
            dto.setFollowedByCurrentUser(followRepository.existsByFollowerAndFollowing(currentUser, u));
        } else {
            dto.setFollowedByCurrentUser(false);
        }

        return dto;
    }
}
