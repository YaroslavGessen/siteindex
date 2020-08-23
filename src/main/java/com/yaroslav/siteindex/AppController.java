package com.yaroslav.siteindex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaroslav.siteindex.util.ElasticsearchUtil;
import com.yaroslav.siteindex.util.KafkaHelper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;


@RestController
@RequestMapping(value = "")
public class AppController {
    @RequestMapping(value = "/app", method = RequestMethod.GET)
    public String sayHello() {
        return "Hello";
    }

    @Autowired
    ElasticsearchUtil elasticsearchUtil;

    @Autowired
    Crawler crawler;

    @Autowired
    KafkaHelper kafka;

    @Autowired
    ObjectMapper om;

    int idx = 0;
    @RequestMapping(value = "/app/sendtokafka", method = RequestMethod.GET)
    public String sendToKafka() {
        kafka.send("hello" + idx++);
        return kafka.recieve(String.class).toString();
    }

    @RequestMapping(value = "/app/crawl1", method = RequestMethod.GET)
    public String crawl(@RequestParam String url) throws IOException {
        return crawler.crawl(url);
    }

    @RequestMapping(value = "/app/searchelastic", method = RequestMethod.GET)
    public JsonNode searchelastic(@RequestParam String keyword) throws IOException {
        return om.readTree(elasticsearchUtil.getData(keyword));
    }

    @PostConstruct
    public void sendOneRecord() {
        kafka.recieve(String.class).toString();
    }
}

