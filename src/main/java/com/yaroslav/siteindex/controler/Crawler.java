package com.yaroslav.siteindex.controler;

import com.yaroslav.siteindex.json.CrawlStatus;
import com.yaroslav.siteindex.json.CrawlerQueueRecord;
import com.yaroslav.siteindex.json.FinishReason;
import com.yaroslav.siteindex.json.State;
import com.yaroslav.siteindex.util.ElasticsearchUtil;
import com.yaroslav.siteindex.util.KafkaHelper;
import com.yaroslav.siteindex.json.UrlSearchDoc;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.IOException;

@Component
public class Crawler {
    private static final int MAX_MINUTES = 2;
    private static final int MAX_DISTANCE_LIMIT = 4;
    private static final int MAX_TIME_LIMIT = 100000;
    private static final int EMPTY_QUEUE_TIME_LIMIT = 10000;

    @Autowired
    private ElasticsearchUtil elasticsearch;
    @Autowired
    private KafkaHelper kafka;

    private static Set<String> visitedUrls = new HashSet<>();
    private static HashMap<String, CrawlStatus> crawlsCollection = new HashMap<>();

    CrawlStatus crawlStatus(String crawlId) {
        return crawlsCollection.get(crawlId);
    }

    String crawl(String url) {
        String crawlId = UUID.randomUUID().toString();
        crawlsCollection.put(crawlId, new CrawlStatus(
                url,
                State.RUNNING,
                System.currentTimeMillis(),
                0,
                FinishReason.NOT_FINISHED));
        sendSingleQueueRecordToKafka(crawlId, url, 0);

        return crawlId;
    }

    @PostConstruct
    public void startListeningToKafka() {
        Thread thread = new Thread(this::run);
        thread.start();
    }

    private void run() {
        System.out.println("Kafka thread running");
        long runningTime = 0;
        long startTime = System.currentTimeMillis();
        while (TimeUnit.MILLISECONDS.toMinutes(runningTime) < MAX_MINUTES) {
            List<CrawlerQueueRecord> queueRecords = kafka.recieve(CrawlerQueueRecord.class);

            for (CrawlerQueueRecord queueRecord : queueRecords) {
                crawlSingleUrl(queueRecord);
            }

            checkCrawlsTimeLimits();
            runningTime = System.currentTimeMillis() - startTime;
        }
    }

    public String searchWithElastic(String crawlId, String text) throws IOException {
        System.out.println(">> receiving data from elastic search: search text->" + text);
        String res = elasticsearch.search(crawlId, text);

        return res.substring(res.indexOf("\"hits\":"));
    }

    public String searchEverythingWithElastic(String text) throws IOException {
        return elasticsearch.searchEverything(text);
    }

    private void crawlSingleUrl(CrawlerQueueRecord queueRecord) {
        String crawlId = queueRecord.getCrawlId();
        String webPageUrl = queueRecord.getUrl();
        int crawlDistance = queueRecord.getDistance() + 1;

        crawlsCollection.get(crawlId).setStartEmptyTime(System.currentTimeMillis());
        if (crawlDistance >= MAX_DISTANCE_LIMIT) {
            updateCrawlStatus(crawlId, FinishReason.MAX_DISTANCE);
        } else {
            process(crawlId, webPageUrl, crawlDistance);
        }
    }

    private void process(String crawlId, String webPageUrl, int crawlDistance) {
        String baseUrl = crawlsCollection.get(crawlId).getBaseUrl();
        Document webPageContent = getWebPageContent(webPageUrl);
        List<String> innerUrls = extractWebPageUrls(baseUrl, webPageContent);
        addUrlsToQueue(crawlId, innerUrls, crawlDistance);
        addElasticSearch(crawlId, baseUrl, webPageUrl, webPageContent, crawlDistance);
    }

    private void checkCrawlsTimeLimits() {
        for (Map.Entry<String, CrawlStatus> crawlStatus : crawlsCollection.entrySet()) {
            if (crawlStatus.getValue().getState() == State.RUNNING) {
                String crawlId = crawlStatus.getKey();
                CrawlStatus status = crawlStatus.getValue();
                if (System.currentTimeMillis() - status.getStartEmptyTime() > EMPTY_QUEUE_TIME_LIMIT) {
                    updateCrawlStatus(crawlId, FinishReason.EMPTY_QUEUE);
                }
                if (System.currentTimeMillis() - status.getStartTime() > MAX_TIME_LIMIT) {
                    updateCrawlStatus(crawlId, FinishReason.TIMEOUT);
                }
            }
        }
    }

    private void updateCrawlStatus(String crawlId, FinishReason finishReason) {
        if (crawlsCollection.get(crawlId).getState() == State.RUNNING) {
            crawlsCollection.get(crawlId).setState(State.FINISHED);
            crawlsCollection.get(crawlId).setFinishReason(finishReason);
        }
    }

    private void addUrlsToQueue(String crawlId, List<String> urls, int distance) {
        System.out.println(">> adding urls to queue: distance->" + distance + " amount->" + urls.size());
        crawlsCollection.get(crawlId).setDistanceFromRoot(distance);
        for (String url : urls) {
            if (!visitedUrls.contains(crawlId + url)) {
                visitedUrls.add(crawlId + url);
                sendSingleQueueRecordToKafka(crawlId, url, distance);
            }
        }
    }

    private void sendSingleQueueRecordToKafka(String crawlId, String url, int distance) {
        System.out.println(">> sending url to kafka: distance->" + distance + "\n   url->" + url);
        CrawlerQueueRecord queueRecord = new CrawlerQueueRecord();
        queueRecord.setCrawlId(crawlId);
        queueRecord.setUrl(url);
        queueRecord.setDistance(distance);
        kafka.send(queueRecord);
    }

    private void addElasticSearch(String crawlId, String baseUrl, String webPageUrl, Document webPageContent, int level) {
        System.out.println(">> adding elastic search for webPage: " + baseUrl);
        String text = String.join(" ", webPageContent.select("a[href]").eachText());
        UrlSearchDoc searchDoc = UrlSearchDoc.of(crawlId, text, webPageUrl, baseUrl, level);
        elasticsearch.addData(searchDoc);
    }

    private List<String> extractWebPageUrls(String baseUrl, Document webPageContent) {
        System.out.println(">> extracting urls from current webPage");
        List<String> links = webPageContent.select("a[href]").eachAttr("abs:href");

        return links.stream().filter(url -> url.startsWith(baseUrl)).collect(Collectors.toList());
    }

    private Document getWebPageContent(String webPageUrl) {
        String content = getHttp(webPageUrl);
        return Jsoup.parse(content);
    }

    private String getHttp(String requestURL) {
        if (requestURL == null || requestURL.trim().equals("")) {
            return "";
        }
        try (Scanner scanner = new Scanner(new URL(requestURL).openStream(),
                StandardCharsets.UTF_8.toString())) {
            scanner.useDelimiter("\\A");

            return scanner.hasNext() ? scanner.next() : "";
        } catch (Exception e) {
            return "";
        }
    }
}