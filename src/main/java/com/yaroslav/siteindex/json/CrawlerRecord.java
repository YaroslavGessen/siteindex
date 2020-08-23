package com.yaroslav.siteindex.json;

import java.util.Objects;

public class CrawlerRecord {
    private String url;
    private int level;

    @Override
    public String toString() {
        return "CrawlerRecord{" +
                "url='" + url + '\'' +
                ", level=" + level +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrawlerRecord that = (CrawlerRecord) o;
        return level == that.level &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, level);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public CrawlerRecord(String url, int level) {
        this.url = url;
        this.level = level;
    }
}
