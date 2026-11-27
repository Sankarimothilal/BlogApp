package com.blogsphere.dto.request;

public class GoogleLoginRequest {

    private String email;
    private String name;
    private String avatarUrl;
    private String idToken;
    private String role; // "Author" or "Reader"

    public GoogleLoginRequest() {}

    public GoogleLoginRequest(String email, String name, String avatarUrl, String idToken, String role) {
        this.email = email;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.idToken = idToken;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
