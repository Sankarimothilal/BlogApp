package com.blogsphere.controller;

import com.blogsphere.entity.Post;
import com.blogsphere.entity.User;
import com.blogsphere.repository.PostRepository;
import com.blogsphere.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:poststatus;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class PostStatusValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmail("rohan@blogsphere.com").orElseThrow();
    }

    @Test
    void createsDraftAndPublishedPosts() throws Exception {
        long draftsBefore = postRepository.countByAuthorAndStatus(owner, "DRAFT");
        long publishedBefore = postRepository.countByAuthorAndStatus(owner, "PUBLISHED");

        mockMvc.perform(post("/api/posts")
                        .with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload("DRAFT")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/posts")
                        .with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload("PUBLISHED")))
                .andExpect(status().isOk());

        assertEquals(draftsBefore + 1, postRepository.countByAuthorAndStatus(owner, "DRAFT"));
        assertEquals(publishedBefore + 1, postRepository.countByAuthorAndStatus(owner, "PUBLISHED"));
    }

    @Test
    void ownerCanUpdatePostToDraft() throws Exception {
        Post post = createPublishedPost();

        mockMvc.perform(put("/api/posts/{id}", post.getId())
                        .with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("DRAFT")))
                .andExpect(status().isOk());

        assertEquals("DRAFT", postRepository.findById(post.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsUnknownStatusOnCreateAndUpdateWithoutChangingStoredStatus() throws Exception {
        long originalPostCount = postRepository.count();
        mockMvc.perform(post("/api/posts")
                        .with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload("ARCHIVED")))
                .andExpect(status().isBadRequest());
        assertEquals(originalPostCount, postRepository.count());

        Post post = createPublishedPost();
        mockMvc.perform(put("/api/posts/{id}", post.getId())
                        .with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("ARCHIVED")))
                .andExpect(status().isBadRequest());
        assertEquals("PUBLISHED", postRepository.findById(post.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsNullEmptyAndMissingStatusOnCreate() throws Exception {
        mockMvc.perform(post("/api/posts").with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload(null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/posts").with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postPayload("")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/posts").with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostRequest("No status", "<p>Body</p>"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNullAndEmptyStatusOnUpdate() throws Exception {
        Post post = createPublishedPost();

        mockMvc.perform(put("/api/posts/{id}", post.getId()).with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/posts/{id}", post.getId()).with(asOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("")))
                .andExpect(status().isBadRequest());

        assertEquals("PUBLISHED", postRepository.findById(post.getId()).orElseThrow().getStatus());
    }

    private Post createPublishedPost() {
        String suffix = UUID.randomUUID().toString();
        Post post = new Post();
        post.setAuthor(owner);
        post.setTitle("Status test " + suffix);
        post.setSlug("status-test-" + suffix);
        post.setContentHtml("<p>Initial content</p>");
        post.setStatus("PUBLISHED");
        return postRepository.saveAndFlush(post);
    }

    private String postPayload(String status) throws Exception {
        return objectMapper.writeValueAsString(new PostRequest("Created " + UUID.randomUUID(), "<p>Body</p>", status));
    }

    private String updatePayload(String status) throws Exception {
        return objectMapper.writeValueAsString(new PostRequest("Updated title", "<p>Updated body</p>", status));
    }

    private UserRequestPostProcessor asOwner() {
        return user(owner.getEmail()).authorities(() -> "ROLE_AUTHOR");
    }

    private record PostRequest(String title, String content, String status) {
        private PostRequest(String title, String content) {
            this(title, content, null);
        }
    }
}
