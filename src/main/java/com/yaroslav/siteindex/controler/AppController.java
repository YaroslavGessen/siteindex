package com.yaroslav.siteindex.controler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaroslav.siteindex.json.CrawlStatus;
import com.yaroslav.siteindex.util.ElasticsearchUtil;
import com.yaroslav.siteindex.util.KafkaHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.IOException;


@RestController
@RequestMapping(value = "")
public class AppController {
    int i = 0;

    @Autowired
    ElasticsearchUtil elasticsearchUtil;

    @Autowired
    Crawler crawler;

    @Autowired
    KafkaHelper kafka;

    @Autowired
    ObjectMapper om;


    @RequestMapping(value = "/app", method = RequestMethod.GET)
    public String sayHello() {
        return "Hello";
    }

    @RequestMapping(value = "/kafka", method = RequestMethod.GET)
    public String testKafka()
    {
        kafka.send("Hello from kafka" + ++i);

        return kafka.recieve(String.class).toString();
    }

    @RequestMapping(value = "/app/crawl1", method = RequestMethod.GET)
    public String crawl(@RequestParam String url) throws IOException {
        return crawler.crawl(url);
    }

    @RequestMapping(value = "/status", method = RequestMethod.GET)
    public CrawlStatus getStatus(String crawlId) {
        return crawler.crawlStatus(crawlId);
    }

    @RequestMapping(value = "/app/searchelastic", method = RequestMethod.GET)
    public JsonNode searchelastic(@RequestParam String keyword) throws IOException {
        return om.readTree(elasticsearchUtil.getData(keyword));
    }

}

