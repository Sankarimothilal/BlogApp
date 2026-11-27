package com.blogsphere.controller;

import com.blogsphere.entity.Bookmark;
import com.blogsphere.entity.Comment;
import com.blogsphere.entity.Like;
import com.blogsphere.entity.Notification;
import com.blogsphere.entity.Post;
import com.blogsphere.entity.ReadingHistory;
import com.blogsphere.entity.Report;
import com.blogsphere.entity.User;
import com.blogsphere.repository.BookmarkRepository;
import com.blogsphere.repository.CommentRepository;
import com.blogsphere.repository.LikeRepository;
import com.blogsphere.repository.NotificationRepository;
import com.blogsphere.repository.PostRepository;
import com.blogsphere.repository.ReadingHistoryRepository;
import com.blogsphere.repository.ReportRepository;
import com.blogsphere.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:postdeletion;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class PostDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User owner;
    private User reader;
    private Post post;
    private Post unrelatedPost;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmail("rohan@blogsphere.com").orElseThrow();
        reader = userRepository.findByEmail("ankit@blogsphere.com").orElseThrow();
        String suffix = UUID.randomUUID().toString();
        post = postRepository.saveAndFlush(createPost(owner, "Delete target " + suffix, "delete-target-" + suffix));
        unrelatedPost = postRepository.saveAndFlush(
                createPost(owner, "Keep post " + suffix, "keep-post-" + suffix));
    }

    @Test
    void ownerCanDeletePostWithoutDependencies() throws Exception {
        long userCount = userRepository.count();
        Long postId = post.getId();

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .with(asUser(owner.getEmail(), "ROLE_AUTHOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Post deleted successfully"));

        assertFalse(postRepository.existsById(postId));
        assertTrue(postRepository.existsById(unrelatedPost.getId()));
        assertEquals(userCount, userRepository.count());
        assertTrue(userRepository.existsById(owner.getId()));
        assertTrue(userRepository.existsById(reader.getId()));
    }

    @Test
    void ownerCanDeletePostWithDependentRecordsWithoutDeletingUnrelatedData() throws Exception {
        Long postId = post.getId();
        String postUrl = "/blog-detail.html?id=" + postId;
        Comment root = commentRepository.saveAndFlush(new Comment(post, reader, null, "Root comment"));
        commentRepository.saveAndFlush(new Comment(post, owner, root, "Reply comment"));
        Comment unrelatedReply = commentRepository.saveAndFlush(
                new Comment(unrelatedPost, reader, root, "Reply on an unrelated post"));
        readingHistoryRepository.saveAndFlush(new ReadingHistory(reader, post));
        likeRepository.saveAndFlush(new Like(reader, post));
        bookmarkRepository.saveAndFlush(new Bookmark(reader, post));
        reportRepository.saveAndFlush(new Report(post, reader, "Test report"));
        notificationRepository.saveAndFlush(new Notification(reader, owner, "COMMENT", "Commented", postUrl));
        Notification unrelatedNotification = notificationRepository.saveAndFlush(new Notification(
                reader, owner, "FOLLOW", "Followed", "/author-profile.html?id=" + owner.getId()));
        long userCount = userRepository.count();

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .with(asUser(owner.getEmail(), "ROLE_AUTHOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Post deleted successfully"));

        assertFalse(postRepository.existsById(postId));
        assertTrue(postRepository.existsById(unrelatedPost.getId()));
        assertEquals(0, commentRepository.countByPost(post));
        assertEquals(0, likeRepository.countByPost(post));
        assertTrue(bookmarkRepository.findByUserAndPost(reader, post).isEmpty());
        assertTrue(readingHistoryRepository.findByUserAndPost(reader, post).isEmpty());
        assertTrue(reportRepository.findAll().stream().noneMatch(report -> report.getPost().getId().equals(postId)));
        assertTrue(notificationRepository.findByRecipientOrderByCreatedAtDesc(reader).stream()
                .noneMatch(notification -> postUrl.equals(notification.getTargetUrl())));
        assertTrue(notificationRepository.existsById(unrelatedNotification.getId()));
        assertTrue(commentRepository.existsById(unrelatedReply.getId()));
        Comment preservedReply = commentRepository.findById(unrelatedReply.getId()).orElseThrow();
        assertEquals("Reply on an unrelated post", preservedReply.getContent());
        assertNull(preservedReply.getParent(), "Reply to the deleted post must remain with its parent detached");
        assertEquals(userCount, userRepository.count());
        assertTrue(userRepository.existsById(owner.getId()));
        assertTrue(userRepository.existsById(reader.getId()));
    }

    @Test
    void unauthorizedUserCannotDeleteAnotherAuthorsPost() throws Exception {
        Long postId = post.getId();

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .with(asUser(reader.getEmail(), "ROLE_READER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));

        assertTrue(postRepository.existsById(postId));
    }

    @Test
    void adminCanDeleteAnotherAuthorsPost() throws Exception {
        Long postId = post.getId();

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .with(asUser("admin@blogsphere.com", "ROLE_ADMIN")))
                .andExpect(status().isOk());

        assertFalse(postRepository.existsById(postId));
        assertTrue(postRepository.existsById(unrelatedPost.getId()));
    }

    private Post createPost(User author, String title, String slug) {
        Post newPost = new Post();
        newPost.setAuthor(author);
        newPost.setTitle(title);
        newPost.setSlug(slug);
        newPost.setContentHtml("<p>Post content</p>");
        newPost.setStatus("PUBLISHED");
        return newPost;
    }

    private UserRequestPostProcessor asUser(String email, String authority) {
        return user(email).authorities(() -> authority);
    }
}
