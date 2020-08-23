package com.yaroslav.siteindex.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaroslav.siteindex.config.KafkaEmbeddedConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KafkaHelper {
    public KafkaHelper(KafkaConsumer<String, String> consumer, KafkaProducer<String, String> producer, ObjectMapper om) {
        this.consumer = consumer;
        this.producer = producer;
        this.om = om;
    }

    KafkaConsumer<String,String> consumer;
    KafkaProducer<String,String> producer;
    ObjectMapper om;


    public  <T> List<T> recieve( Class<T> targetClazz)  {
        List<T> res = new ArrayList<>();
        consumer.poll(Duration.ofSeconds(1)).forEach(x-> {
            try {
                res.add(
                        om.readValue(x.value(), targetClazz)
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return res;
    }

    public  <T> boolean send(T record)  {
        try {
            String message = om.writeValueAsString(record);
            producer.send(new ProducerRecord<String,String>(KafkaEmbeddedConfig.TEST_TOPIC, UUID.randomUUID().toString(),message)).get();
            producer.flush();
            return true;
        }catch (Exception e) {
            return false;
        }
    }
}
