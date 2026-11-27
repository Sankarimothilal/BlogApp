/**
 * BlogSphere Centralized API Service
 * Connects to Spring Boot backend at http://localhost:8080/api
 * Uses the Spring Boot backend for application data.
 */

const API_BASE = 'http://localhost:8080/api';

// --- Local Storage Helpers ---
const STORAGE_TOKEN_KEY = 'blogsphere_token';
const STORAGE_USER_KEY = 'blogsphere_user';

// Generic Fetch function with JWT Authorization
async function request(endpoint, options = {}) {
  const token = localStorage.getItem(STORAGE_TOKEN_KEY);
  const url = `${API_BASE}${endpoint}`;
  const method = (options.method || 'GET').toUpperCase();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    ...(options.headers || {})
  };

  try {
    const res = await fetch(url, { ...options, headers });
    if (!res.ok) {
      if (res.status === 401) {
        localStorage.removeItem(STORAGE_TOKEN_KEY);
        localStorage.removeItem(STORAGE_USER_KEY);
      }
      const errData = await res.json().catch(() => ({}));
      const error = new Error(errData.message || errData.error || `HTTP error ${res.status}`);
      error.status = res.status;
      error.data = errData;
      error.url = url;
      error.method = method;
      throw error;
    }
    const data = await res.json();
    if (data && data.success === false) {
      const error = new Error(data.message || data.error || 'The request was not completed successfully.');
      error.status = res.status;
      error.data = data;
      error.url = url;
      error.method = method;
      throw error;
    }
    return data;
  } catch (error) {
    throw error;
  }
}

// Global API Object
const API = {
  // Auth methods
  auth: {
    async login(email, password) {
      const res = await request('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password })
      });
      if (!res.data || !res.data.token) throw new Error(res.message || 'Login response was incomplete.');
      localStorage.setItem(STORAGE_TOKEN_KEY, res.data.token);
      localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(res.data));
      return res.data;
    },

    async register(fullName, email, password, role) {
      const res = await request('/auth/register', {
        method: 'POST',
        body: JSON.stringify({ fullName, email, password, role })
      });
      if (!res.data || !res.data.token) throw new Error(res.message || 'Registration response was incomplete.');
      localStorage.setItem(STORAGE_TOKEN_KEY, res.data.token);
      localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(res.data));
      return res.data;
    },

    async sendPasswordResetCode(email) {
      const res = await request('/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email })
      });
      return res.data || res;
    },

    async verifyResetCode(email, code) {
      const res = await request('/auth/verify-code', {
        method: 'POST',
        body: JSON.stringify({ email, code })
      });
      return res.data || res;
    },

    async resetPassword(resetToken, newPassword) {
      const res = await request('/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ resetToken, newPassword })
      });
      return res.data || res;
    },

    async logout() {
      try {
        await request('/auth/logout', { method: 'POST' });
      } catch (error) {
        // Always clear local authentication if the server cannot be reached.
      } finally {
        localStorage.removeItem(STORAGE_TOKEN_KEY);
        localStorage.removeItem(STORAGE_USER_KEY);
        window.location.href = 'login.html';
      }
    },

    getCurrentUser() {
      const u = localStorage.getItem(STORAGE_USER_KEY);
      return u ? JSON.parse(u) : null;
    },

    isAuthenticated() {
      return !!localStorage.getItem(STORAGE_TOKEN_KEY);
    }
  },

  // User & Settings methods
  users: {
    async uploadAvatar(file) {
      const formData = new FormData();
      formData.append('file', file);
      const token = localStorage.getItem(STORAGE_TOKEN_KEY);
      const response = await fetch(`${API_BASE}/users/profile/avatar`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok || result.success === false || !result.data?.avatarUrl) {
        const error = new Error(result.message || result.error || `HTTP error ${response.status}`);
        error.status = response.status;
        error.data = result;
        throw error;
      }
      const avatarUrl = new URL(result.data.avatarUrl, API_BASE).toString();
      const currentUser = API.auth.getCurrentUser();
      if (currentUser) {
        currentUser.avatarUrl = avatarUrl;
        localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));
      }
      return { ...result.data, avatarUrl };
    },

    async updateProfile(profileData) {
      const res = await request('/users/profile', {
        method: 'PUT',
        body: JSON.stringify(profileData)
      });
      const currentUser = API.auth.getCurrentUser();
      if (currentUser) {
        currentUser.fullName = profileData.fullName || currentUser.fullName;
        currentUser.bio = profileData.bio !== undefined ? profileData.bio : currentUser.bio;
        currentUser.avatarUrl = profileData.avatarUrl || currentUser.avatarUrl;
        localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(currentUser));
      }
      return res.data;
    },

    async changePassword(passwordData) {
      return await request('/users/password', {
        method: 'PUT',
        body: JSON.stringify(passwordData)
      });
    },

    async getReaderDashboard() {
      const res = await request('/users/reader-dashboard');
      return res.data;
    },

    async getAllAuthors() {
      const res = await request('/users/authors');
      return res.data;
    }
  },

  media: {
    async uploadImage(file) {
      const formData = new FormData();
      formData.append('file', file);
      const token = localStorage.getItem(STORAGE_TOKEN_KEY);
      const response = await fetch(`${API_BASE}/media/upload`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok || result.success === false || !result.data?.url) {
        const error = new Error(result.message || result.error || `HTTP error ${response.status}`);
        error.status = response.status;
        error.data = result;
        throw error;
      }
      return {
        ...result.data,
        url: new URL(result.data.url, API_BASE).toString()
      };
    }
  },

  // Post methods
  posts: {
    async getMine(params = {}) {
      const qs = new URLSearchParams(params).toString();
      const res = await request(`/users/me/posts${qs ? `?${qs}` : ''}`);
      return res.data;
    },

    async getAll(params = {}) {
      const qs = new URLSearchParams(params).toString();
      const res = await request(`/posts?${qs}`);
      return res.data;
    },

    async getFeatured() {
      const res = await request('/posts/featured');
      return res.data;
    },

    async getById(id) {
      const res = await request(`/posts/${id}`);
      return res.data;
    },

    async create(postData) {
      const res = await request('/posts', {
        method: 'POST',
        body: JSON.stringify(postData)
      });
      return res.data;
    },

    async update(id, postData) {
      const res = await request(`/posts/${id}`, {
        method: 'PUT',
        body: JSON.stringify(postData)
      });
      return res.data;
    },

    async delete(id) {
      const postId = Number(id);
      if (!Number.isSafeInteger(postId) || postId <= 0) throw new Error('A valid blog ID is required to delete this blog.');
      const res = await request(`/posts/${postId}`, { method: 'DELETE' });
      if (!res || res.success !== true) {
        const error = new Error(res?.message || 'The server did not confirm blog deletion.');
        error.status = 200;
        error.method = 'DELETE';
        error.url = `${API_BASE}/posts/${postId}`;
        throw error;
      }
      return true;
    }
  },

  // Comments
  comments: {
    async getByPost(postId) {
      const res = await request(`/posts/${postId}/comments`);
      return res.data;
    },

    async add(postId, content) {
      const res = await request(`/posts/${postId}/comments`, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      if (!res.data) throw new Error(res.message || 'Comment was not saved.');
      return res.data;
    }
  },

  // Interactions (Like, Bookmark, Follow)
  interactions: {
    async toggleLike(postId) {
      const res = await request(`/posts/${postId}/like`, { method: 'POST' });
      if (!res.data) throw new Error(res.message || 'Like was not updated.');
      return res.data;
    },

    async toggleBookmark(postId) {
      const res = await request(`/posts/${postId}/bookmark`, { method: 'POST' });
      if (!res.data) throw new Error(res.message || 'Bookmark was not updated.');
      return res.data;
    },

    async toggleFollow(authorId) {
      const res = await request(`/users/${authorId}/follow`, { method: 'POST' });
      if (!res.data) throw new Error(res.message || 'Follow status was not updated.');
      return res.data;
    }
  },

  // Categories
  categories: {
    async getAll() {
      const res = await request('/categories');
      return res.data;
    }
  },

  // Dashboards
  dashboard: {
    async getUserDashboard() {
      const res = await request('/users/dashboard');
      return res.data;
    },

    async getAdminDashboard() {
      const res = await request('/admin/dashboard');
      return res.data;
    }
  },

  // Admin Methods (Issues 2 & 3)
  admin: {
    async getAllUsers() {
      const res = await request('/admin/users');
      return res.data;
    },

    async updateUserRole(userId, newRole) {
      const res = await request(`/admin/users/${userId}/role`, {
        method: 'PUT',
        body: JSON.stringify({ role: newRole })
      });
      return res.data;
    },

    async deleteUser(userId) {
      await request(`/admin/users/${userId}`, { method: 'DELETE' });
      return true;
    },

    async getAllComments() {
      const res = await request('/admin/comments');
      return res.data;
    },

    async deleteComment(commentId) {
      await request(`/admin/comments/${commentId}`, { method: 'DELETE' });
      return true;
    },

    async updateReportStatus(reportId, status = 'REVIEWED') {
      const res = await request(`/admin/reports/${reportId}`, {
        method: 'PUT',
        body: JSON.stringify({ status })
      });
      return res.data;
    }
  },

  // Notifications
  notifications: {
    async getAll() {
      const res = await request('/users/notifications');
      return res.data;
    }
  }
};
