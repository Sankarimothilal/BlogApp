package com.blogsphere.service;

import com.blogsphere.dto.request.CreatePostRequest;
import com.blogsphere.dto.response.PostDetailDTO;
import com.blogsphere.dto.response.PostSummaryDTO;
import com.blogsphere.entity.*;
import com.blogsphere.exception.ResourceNotFoundException;
import com.blogsphere.repository.*;
import com.blogsphere.util.HtmlContentSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public PostDetailDTO createPost(CreatePostRequest request, User author) {
        Post post = new Post();
        post.setAuthor(author);
        post.setTitle(request.getTitle());
        post.setSlug(generateSlug(request.getTitle()));
        post.setContentHtml(HtmlContentSanitizer.sanitize(request.getContent()));
        post.setSummary(request.getSummary() != null && !request.getSummary().isBlank() ?
                HtmlContentSanitizer.sanitize(request.getSummary()) : generateSummary(post.getContentHtml()));
        post.setCoverImage(request.getCoverImage());
        post.setStatus(normalizePostStatus(request.getStatus()));
        post.setViewCount(0L);

        // Assign Category
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            Category cat = categoryRepository.findByName(request.getCategory())
                    .orElseGet(() -> categoryRepository.save(new Category(request.getCategory(), generateSlug(request.getCategory()), "folder", "")));
            post.setCategory(cat);
        }

        // Assign Tags
        if (request.getTags() != null) {
            Set<Tag> tags = new HashSet<>();
            for (String tagName : request.getTags()) {
                String cleanTag = tagName.trim().replaceAll("^#", "");
                if (!cleanTag.isBlank()) {
                    Tag tag = tagRepository.findByName(cleanTag)
                            .orElseGet(() -> tagRepository.save(new Tag(cleanTag, generateSlug(cleanTag))));
                    tags.add(tag);
                }
            }
            post.setTags(tags);
        }

        Post saved = postRepository.save(post);

        // If published, notify followers
        if ("PUBLISHED".equals(saved.getStatus())) {
            notificationService.notifyFollowersNewPost(author, saved);
        }

        return getPostDetails(saved.getId());
    }

    @Transactional
    public PostDetailDTO updatePost(Long id, CreatePostRequest request, User currentUser) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));

        if (!post.getAuthor().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ROLE_ADMIN) {
            if (!"PUBLISHED".equals(post.getStatus())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
            }
            throw new AccessDeniedException("You do not have permission to edit this post");
        }

        post.setTitle(request.getTitle());
        post.setContentHtml(HtmlContentSanitizer.sanitize(request.getContent()));
        post.setSummary(request.getSummary() != null && !request.getSummary().isBlank() ?
                HtmlContentSanitizer.sanitize(request.getSummary()) : generateSummary(post.getContentHtml()));
        if (request.getCoverImage() != null) {
            post.setCoverImage(request.getCoverImage());
        }
        post.setStatus(normalizePostStatus(request.getStatus()));
        post.setUpdatedAt(LocalDateTime.now());

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            Category cat = categoryRepository.findByName(request.getCategory())
                    .orElseGet(() -> categoryRepository.save(new Category(request.getCategory(), generateSlug(request.getCategory()), "folder", "")));
            post.setCategory(cat);
        }

        if (request.getTags() != null) {
            Set<Tag> tags = new HashSet<>();
            for (String tagName : request.getTags()) {
                String cleanTag = tagName.trim().replaceAll("^#", "");
                if (!cleanTag.isBlank()) {
                    Tag tag = tagRepository.findByName(cleanTag)
                            .orElseGet(() -> tagRepository.save(new Tag(cleanTag, generateSlug(cleanTag))));
                    tags.add(tag);
                }
            }
            post.setTags(tags);
        }

        Post updated = postRepository.save(post);
        return getPostDetails(updated.getId());
    }

    @Transactional
    public void deletePost(Long id, User currentUser) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));

        if (!post.getAuthor().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ROLE_ADMIN) {
            if (!"PUBLISHED".equals(post.getStatus())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
            }
            throw new AccessDeniedException("You do not have permission to delete this post");
        }

        Long postId = post.getId();
        List<Long> commentIds = commentRepository.findIdsByPostId(postId);
        if (!commentIds.isEmpty()) {
            commentRepository.detachRepliesToComments(commentIds);
        }
        commentRepository.deleteForPost(postId);
        likeRepository.deleteForPost(postId);
        bookmarkRepository.deleteForPost(postId);
        readingHistoryRepository.deleteForPost(postId);
        reportRepository.deleteForPost(postId);
        notificationRepository.deleteByTargetUrl("/blog-detail.html?id=" + postId);
        postRepository.delete(post);
    }

    @Transactional
    public PostDetailDTO getPostDetails(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));

        User currentUser = authService.getCurrentAuthenticatedUser();
        if (!"PUBLISHED".equals(post.getStatus()) && !canAccessUnpublishedPost(post, currentUser)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        // Increment view count
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);

        if (currentUser != null) {
            // Record reading history
            ReadingHistory history = readingHistoryRepository.findByUserAndPost(currentUser, post)
                    .orElse(new ReadingHistory(currentUser, post));
            history.setViewedAt(LocalDateTime.now());
            readingHistoryRepository.save(history);
        }

        return mapToDetailDTO(post, currentUser);
    }

    public Page<PostSummaryDTO> getAllPublishedPosts(String query, String categorySlug, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Post> postPage;

        if (query != null && !query.isBlank()) {
            postPage = postRepository.searchPosts(query.trim(), pageable);
        } else if (categorySlug != null && !categorySlug.isBlank()) {
            Category cat = categoryRepository.findBySlug(categorySlug)
                    .orElse(null);
            if (cat != null) {
                postPage = postRepository.findByStatusAndCategory("PUBLISHED", cat, pageable);
            } else {
                postPage = postRepository.findByStatus("PUBLISHED", pageable);
            }
        } else {
            postPage = postRepository.findByStatus("PUBLISHED", pageable);
        }

        return postPage.map(this::mapToSummaryDTO);
    }

    public List<PostSummaryDTO> getFeaturedPosts() {
        List<Post> posts = postRepository.findTop5ByStatusOrderByViewCountDesc("PUBLISHED");
        return posts.stream().map(this::mapToSummaryDTO).collect(Collectors.toList());
    }

    public Page<PostSummaryDTO> getPostsByAuthor(User author, String status, int page, int size, User currentUser) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String requestedStatus = status == null || status.isBlank() ? "PUBLISHED" : status.trim().toUpperCase();

        if (!"PUBLISHED".equals(requestedStatus) && !canAccessAuthorPosts(author, currentUser)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Posts not found");
        }

        Page<Post> postPage = postRepository.findByAuthorAndStatus(author, requestedStatus, pageable);
        return postPage.map(this::mapToSummaryDTO);
    }

    private String normalizePostStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"DRAFT".equals(normalized) && !"PUBLISHED".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be DRAFT or PUBLISHED");
        }
        return normalized;
    }

    private boolean canAccessUnpublishedPost(Post post, User currentUser) {
        return currentUser != null && (currentUser.getRole() == Role.ROLE_ADMIN
                || post.getAuthor().getId().equals(currentUser.getId()));
    }

    private boolean canAccessAuthorPosts(User author, User currentUser) {
        return currentUser != null && (currentUser.getRole() == Role.ROLE_ADMIN
                || author.getId().equals(currentUser.getId()));
    }

    public PostSummaryDTO mapToSummaryDTO(Post post) {
        PostSummaryDTO dto = new PostSummaryDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(HtmlContentSanitizer.sanitize(post.getSummary()));
        dto.setCoverImage(post.getCoverImage());
        dto.setCategory(post.getCategory() != null ? post.getCategory().getName() : "General");
        dto.setTags(post.getTags().stream().map(Tag::getName).collect(Collectors.toList()));
        dto.setAuthorId(post.getAuthor().getId());
        dto.setAuthorName(post.getAuthor().getFullName());
        dto.setAuthorAvatar(post.getAuthor().getAvatarUrl());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setViewCount(post.getViewCount());
        dto.setCommentCount(commentRepository.countByPost(post));
        dto.setLikeCount(likeRepository.countByPost(post));
        dto.setStatus(post.getStatus());
        return dto;
    }

    private PostDetailDTO mapToDetailDTO(Post post, User currentUser) {
        PostDetailDTO dto = new PostDetailDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setSlug(post.getSlug());
        dto.setSummary(HtmlContentSanitizer.sanitize(post.getSummary()));
        dto.setContentHtml(HtmlContentSanitizer.sanitize(post.getContentHtml()));
        dto.setCoverImage(post.getCoverImage());
        dto.setCategory(post.getCategory() != null ? post.getCategory().getName() : "General");
        dto.setTags(post.getTags().stream().map(Tag::getName).collect(Collectors.toList()));
        dto.setAuthorId(post.getAuthor().getId());
        dto.setAuthorName(post.getAuthor().getFullName());
        dto.setAuthorBio(post.getAuthor().getBio());
        dto.setAuthorAvatar(post.getAuthor().getAvatarUrl());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setViewCount(post.getViewCount());
        dto.setCommentCount(commentRepository.countByPost(post));
        dto.setLikeCount(likeRepository.countByPost(post));

        if (currentUser != null) {
            dto.setLikedByCurrentUser(likeRepository.existsByUserAndPost(currentUser, post));
            dto.setBookmarkedByCurrentUser(bookmarkRepository.existsByUserAndPost(currentUser, post));
        } else {
            dto.setLikedByCurrentUser(false);
            dto.setBookmarkedByCurrentUser(false);
        }

        return dto;
    }

    private String generateSlug(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
        return slug + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private String generateSummary(String html) {
        String text = html.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").trim();
        return text.length() > 160 ? text.substring(0, 157) + "..." : text;
    }
}
