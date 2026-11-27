# BlogSphere - Full-Stack Blogging Platform

BlogSphere is a modern, responsive, full-stack blogging platform built with **Spring Boot (Java 17)**, **MySQL**, and a decoupled **HTML5/CSS3/JavaScript (ES6+)** frontend.

The UI is designed to faithfully match the wireframes and design system of the BlogSphere specification.

---

## 📁 Repository & Folder Structure

```
blogsphere/
├── backend/                       # Spring Boot 3 + Java 17 + MySQL Backend
│   ├── src/main/java/com/blogsphere/
│   │   ├── config/                # Security, CORS, and Data Initializer
│   │   ├── controller/            # REST API Controllers
│   │   ├── dto/                   # Request & Response DTOs
│   │   ├── entity/                # JPA Database Entities
│   │   ├── exception/             # Global Exception Handling
│   │   ├── repository/            # Spring Data JPA Repositories
│   │   ├── security/              # Spring Security 6 & JWT Token Filter
│   │   └── service/               # Business Logic Layer
│   ├── src/main/resources/
│   │   ├── application.yml        # Database, JWT & server configuration
│   │   ├── schema.sql             # MySQL DDL table schema
│   │   └── data.sql               # Initial sample seed data (Admin, Authors, Posts)
│   └── pom.xml                    # Maven project dependencies
│
├── frontend/                      # Pure HTML5, CSS3 & JavaScript Frontend
│   ├── assets/
│   │   ├── css/
│   │   │   ├── variables.css      # Theme colors (Light & Dark), typography
│   │   │   └── style.css          # Main responsive styling & UI components
│   │   └── js/
│   │       ├── api.js             # Centralized Fetch client with JWT & Mock Fallback
│   │       └── common.js          # Shared navbar, auth guard, dark mode toggle
│   ├── index.html                 # 1. Home / Landing & Featured Blogs
│   ├── login.html                 # 2. Login Screen
│   ├── register.html              # 3. Register Screen (Role: Author / Reader)
│   ├── forgot-password.html       # 4. Forgot Password Screen
│   ├── dashboard.html             # 5. User / Author Dashboard
│   ├── create-blog.html           # 6. Create / Edit Blog & Rich Editor
│   ├── blog-detail.html           # 7 & 8. Blog Reading & Comments Section
│   ├── author-profile.html        # 9. Author Profile & Published Blogs
│   ├── admin-dashboard.html       # 10. Admin Dashboard & Content Moderation
│   ├── categories.html            # 11. Categories Explorer Page
│   └── notifications.html         # 12. User Notifications Feed
│
└── README.md
```

---

## 🚀 Quick Start Guide

### 1. Database Setup (MySQL)
1. Ensure MySQL server is running.
2. The application will automatically create the database `blogsphere_db` if it doesn't exist, or you can run:
   ```sql
   CREATE DATABASE IF NOT EXISTS blogsphere_db;
   ```
3. Update database credentials in `backend/src/main/resources/application.yml` if your MySQL username/password is different:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/blogsphere_db?createDatabaseIfNotExist=true&useSSL=false
       username: root
       password: your_mysql_password
   ```

### 2. Run the Backend (Spring Boot)
Open a terminal in the `backend/` folder:
```bash
cd backend
mvn spring-boot:run
```
* The backend server starts at: `http://localhost:8080`
* Default Admin login: `admin@blogsphere.com` / `admin123`
* Default Author login: `rohan@blogsphere.com` / `password123`

### 3. Run the Frontend (HTML/CSS/JS)
You can open any frontend HTML file directly in any modern browser!
For the best experience (e.g. avoiding local CORS file restrictions):
* Open the `frontend/` directory in VS Code and click **"Go Live"** (Live Server extension).
* Or run a simple HTTP server:
  ```bash
  cd frontend
  # Using Python:
  python -m http.server 5500
  # Or using Node:
  npx serve .
  ```
* Open `http://localhost:5500` in your web browser.

> **Zero-Friction Offline / Standalone Mode**:
> If the Spring Boot backend is not running, the frontend's `api.js` automatically falls back to built-in mock data so that all 12 UI screens can be previewed, navigated, and tested immediately! Once the backend is up, it seamlessly switches to live REST endpoints.

---

## 🎨 UI Features
* **100% Visual Fidelity**: Built directly according to the 12 wireframe specifications in the BlogSphere design.
* **Light & Dark Theme**: Toggle anytime via the Moon/Sun icon in the header.
* **Responsive Layout**: Designed for mobile, tablet, and widescreen desktop displays.
* **Role-Based Views**: Reader, Author, and Admin access levels.
