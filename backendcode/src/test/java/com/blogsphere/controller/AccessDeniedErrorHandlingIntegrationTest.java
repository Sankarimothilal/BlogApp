package com.blogsphere.controller;

import com.blogsphere.entity.Post;
import com.blogsphere.entity.Role;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:accesscontrol;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class AccessDeniedErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User owner;
    private User otherAuthor;
    private Post draft;

    @BeforeEach
    void setUp() {
        owner = userRepository.findByEmail("rohan@blogsphere.com").orElseThrow();
        String suffix = UUID.randomUUID().toString();
        otherAuthor = userRepository.save(new User(
                "Other Author", "other-" + suffix + "@example.test", "unused", Role.ROLE_AUTHOR));

        draft = new Post();
        draft.setAuthor(owner);
        draft.setTitle("Private Draft " + suffix);
        draft.setSlug("private-draft-" + suffix);
        draft.setContentHtml("<p>Draft content</p>");
        draft.setStatus("DRAFT");
        draft = postRepository.saveAndFlush(draft);
    }

    @Test
    void readerCannotCreatePostAndReceivesForbidden() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(asUser("ankit@blogsphere.com", "ROLE_READER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Reader post\",\"content\":\"content\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));
    }

    @Test
    void anotherAuthorCannotDiscoverOrEditAnOwnersDraft() throws Exception {
        mockMvc.perform(put("/api/posts/{id}", draft.getId())
                        .with(asUser(otherAuthor.getEmail(), "ROLE_AUTHOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postUpdate("Attempted edit")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Post not found"));
    }

    @Test
    void ownerCanEditOwnDraft() throws Exception {
        mockMvc.perform(put("/api/posts/{id}", draft.getId())
                        .with(asUser(owner.getEmail(), "ROLE_AUTHOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postUpdate("Owner edit")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanEditAnotherAuthorsDraft() throws Exception {
        mockMvc.perform(put("/api/posts/{id}", draft.getId())
                        .with(asUser("admin@blogsphere.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postUpdate("Admin edit")))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdminIsDeniedAdminEndpointsWithConsistentJson() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .with(asUser(owner.getEmail(), "ROLE_AUTHOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));
    }

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .with(asUser("admin@blogsphere.com", "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void unexpectedFailuresRemainGenericInternalServerErrors() throws Exception {
        mockMvc.perform(get("/api/posts").param("page", "-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    private UserRequestPostProcessor asUser(String email, String authority) {
        return user(email).authorities(() -> authority);
    }

    private String postUpdate(String title) throws Exception {
        return objectMapper.writeValueAsString(new PostUpdateRequest(title, "<p>Updated content</p>", "DRAFT"));
    }

    private record PostUpdateRequest(String title, String content, String status) {}
}
