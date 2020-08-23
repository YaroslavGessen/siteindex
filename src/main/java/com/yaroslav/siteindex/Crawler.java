package com.yaroslav.siteindex;

import com.yaroslav.siteindex.json.CrawlerRecord;
import com.yaroslav.siteindex.util.ElasticsearchUtil;
import com.yaroslav.siteindex.util.KafkaHelper;
import com.yaroslav.tinyurl.json.UrlSearchDoc;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class Crawler {
    private static final int MAX_DISTANCE = 20;
    private static final long MAX_TIME_MILLIS = 120000;
    private static final long EMPTY_QUEUE_TIMEOUT = 10000;
    public static final int MAX_TIME_CLEAR = 10;
    Set<String> visited = new HashSet<>();
    String baseUrl = "";

    @Autowired
    KafkaHelper kafka;
    @Autowired
    ElasticsearchUtil elasticsearchUtil;
    long lastSeenDataTime;
    public String crawl(String baseUrl) throws IOException {
        clearKafka();
        this.baseUrl = baseUrl;
        long startTime = System.currentTimeMillis();
        lastSeenDataTime = startTime;
        UrlsAndText urls = getUrls(baseUrl);
        urls.getUrls().forEach(url -> kafka.send(new CrawlerRecord(url, 0)));
        while (System.currentTimeMillis() < (startTime + MAX_TIME_MILLIS)){
            List<CrawlerRecord> crawlerRecords = kafka.recieve(CrawlerRecord.class);
            if (shouldStopEmptyQueue(crawlerRecords)) return "Finished empty queue";
            if (crawlerRecords.size() > 0 && crawlerRecords.get(0).getLevel() == MAX_DISTANCE) return "finished Max Distance";
            crawlerRecords.forEach(record -> {
                   if (!"".equals(record.getUrl()) && !visited.contains(record.getUrl())){
                       visited.add(record.getUrl());
                       try {
                           System.out.println("Working on: " + record.getUrl() + " distance from root: " + record.getLevel());
                           UrlsAndText newurls = getUrls(record.getUrl());
                           indexElasticSearch(newurls, record, baseUrl);
                           newurls.getUrls().stream().filter(url -> url.startsWith(baseUrl))
                           .forEach(newurl -> kafka.send(new CrawlerRecord(newurl, record.getLevel() + 1)));
                       } catch (IOException e) {
                           e.printStackTrace();
                       }
                   }
            });
        }
        return "Finsihed Time Out";
    }

    private void clearKafka() {
        for (int i = 0; i < MAX_TIME_CLEAR; i++) {
            kafka.recieve(CrawlerRecord.class);
        }
    }

    private void indexElasticSearch(UrlsAndText newurls, CrawlerRecord record, String baseUrl) {
        elasticsearchUtil.addData(UrlSearchDoc.of(newurls.getText(), record.getUrl(), baseUrl, record.getLevel()));
    }

    private boolean shouldStopEmptyQueue(List<CrawlerRecord> crawlerRecords) {
        if (crawlerRecords.size() == 0 && System.currentTimeMillis() > (lastSeenDataTime + EMPTY_QUEUE_TIMEOUT)){
            return true;
        }
        if (crawlerRecords.size() > 0 ) lastSeenDataTime = System.currentTimeMillis();
        return false;
    }


    public String getHttp(String requestURL) throws IOException
    {
        try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
                StandardCharsets.UTF_8.toString()))
        {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
    public UrlsAndText getUrls(String url) throws IOException {
        List<String> urls = new ArrayList<>();
        String text = "";
        String content = getHttp(url);
        Document doc = Jsoup.parse(content);

        Elements links = doc.select("a[href]");
        for (Element link : links) {
            urls.add(link.attr("abs:href"));
            text += " " + link.text();
        }
        return new UrlsAndText(urls, text);
    }

    static class UrlsAndText {
        List<String> urls;
        String text;

        public List<String> getUrls() {
            return urls;
        }

        public void setUrls(List<String> urls) {
            this.urls = urls;
        }

        @Override
        public String toString() {
            return "UrlsAndText{" +
                    "urls=" + urls +
                    ", text='" + text + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UrlsAndText that = (UrlsAndText) o;
            return Objects.equals(urls, that.urls) &&
                    Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(urls, text);
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public UrlsAndText(List<String> urls, String text) {
            this.urls = urls;
            this.text = text;
        }
    }
}
