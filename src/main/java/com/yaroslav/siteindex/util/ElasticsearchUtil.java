package com.yaroslav.siteindex.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;
import com.yaroslav.tinyurl.json.UrlSearchDoc;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ElasticsearchUtil {

    @Value("${elastic.base.url}")
    private String baseUrl;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper om;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);


    public  void addData(UrlSearchDoc doc)  {
        try {
            String auth =  new String(Base64.encodeBase64("site:4610c374b4b7ae2bd80f0a8d1384a12f".getBytes()));
            String content = om.writeValueAsString(doc);
            OkHttpClient client =  new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, content);
            System.out.println(om.writeValueAsString(doc));
            Request request = new Request.Builder()
                    .url(baseUrl +  "/doc")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build();
            Response res =  client.newCall(request).execute();
            System.out.println(res.body().string());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public String getData(String keyword) {
        try {
            String auth = new String(Base64.encodeBase64("site:4610c374b4b7ae2bd80f0a8d1384a12f".getBytes()));
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json");
            Request request = new Request.Builder()
                    .url(baseUrl + "/_search?q=" + keyword)
                    .method("GET", null)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                    .build();
            Response res = client.newCall(request).execute();
            return res.body().string();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}