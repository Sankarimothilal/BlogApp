package com.blogsphere.dto.response;

import java.util.List;
import java.util.Map;

public class UserDashboardStatsDTO {

    private long publishedBlogs;
    private long drafts;
    private long totalViews;
    private long totalLikes;
    private long totalComments;
    private long totalBookmarks;
    private double engagementRate;
    private long avgViewsPerArticle;
    private String topPerformingBlogTitle;
    private long topPerformingBlogViews;
    private Map<String, Long> weeklyActivity;
    private List<ActivityItemDTO> recentActivity;

    public UserDashboardStatsDTO() {}

    public UserDashboardStatsDTO(long publishedBlogs, long drafts, long totalViews, long totalLikes, List<ActivityItemDTO> recentActivity) {
        this.publishedBlogs = publishedBlogs;
        this.drafts = drafts;
        this.totalViews = totalViews;
        this.totalLikes = totalLikes;
        this.recentActivity = recentActivity;
    }

    public long getPublishedBlogs() {
        return publishedBlogs;
    }

    public void setPublishedBlogs(long publishedBlogs) {
        this.publishedBlogs = publishedBlogs;
    }

    public long getDrafts() {
        return drafts;
    }

    public void setDrafts(long drafts) {
        this.drafts = drafts;
    }

    public long getTotalViews() {
        return totalViews;
    }

    public void setTotalViews(long totalViews) {
        this.totalViews = totalViews;
    }

    public long getTotalLikes() {
        return totalLikes;
    }

    public void setTotalLikes(long totalLikes) {
        this.totalLikes = totalLikes;
    }

    public long getTotalComments() {
        return totalComments;
    }

    public void setTotalComments(long totalComments) {
        this.totalComments = totalComments;
    }

    public long getTotalBookmarks() {
        return totalBookmarks;
    }

    public void setTotalBookmarks(long totalBookmarks) {
        this.totalBookmarks = totalBookmarks;
    }

    public double getEngagementRate() {
        return engagementRate;
    }

    public void setEngagementRate(double engagementRate) {
        this.engagementRate = engagementRate;
    }

    public long getAvgViewsPerArticle() {
        return avgViewsPerArticle;
    }

    public void setAvgViewsPerArticle(long avgViewsPerArticle) {
        this.avgViewsPerArticle = avgViewsPerArticle;
    }

    public String getTopPerformingBlogTitle() {
        return topPerformingBlogTitle;
    }

    public void setTopPerformingBlogTitle(String topPerformingBlogTitle) {
        this.topPerformingBlogTitle = topPerformingBlogTitle;
    }

    public long getTopPerformingBlogViews() {
        return topPerformingBlogViews;
    }

    public void setTopPerformingBlogViews(long topPerformingBlogViews) {
        this.topPerformingBlogViews = topPerformingBlogViews;
    }

    public Map<String, Long> getWeeklyActivity() {
        return weeklyActivity;
    }

    public void setWeeklyActivity(Map<String, Long> weeklyActivity) {
        this.weeklyActivity = weeklyActivity;
    }

    public List<ActivityItemDTO> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<ActivityItemDTO> recentActivity) {
        this.recentActivity = recentActivity;
    }

    public static class ActivityItemDTO {
        private String type; // LIKE, COMMENT, PUBLISH
        private String text;
        private String timestamp;

        public ActivityItemDTO() {}

        public ActivityItemDTO(String type, String text, String timestamp) {
            this.type = type;
            this.text = text;
            this.timestamp = timestamp;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}
