package com.yaroslav.siteindex.util;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaroslav.siteindex.json.UrlSearchDoc;
import com.squareup.okhttp.*;
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
    private static final String ELASTIC_SEARCH_URL =
            "https://site:4610c374b4b7ae2bd80f0a8d1384a12f@gimli-eu-west-1.searchly.com/elastic/_search";
    private static final String API_KEY = "site:4610c374b4b7ae2bd80f0a8d1384a12f";

    @Value("${elastic.base.url}")
    private String baseUrl;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper om;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);


    public  void addData(UrlSearchDoc doc)  {
        try {
            String auth =  new String(Base64.encodeBase64(API_KEY.getBytes()));
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
            Response response =  client.newCall(request).execute();
            //  System.out.println("@@@@" + response.body().string());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public String searchEverything(String text) throws IOException {
        String auth =  new String(Base64.encodeBase64(API_KEY.getBytes()));
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        Request request = new Request.Builder()
                .url(baseUrl + "/_search?q=" + text)
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    public String search(String crawlId, String text) throws IOException {
        String auth =  new String(Base64.encodeBase64(API_KEY.getBytes()));
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, buildBody(crawlId, text));
        Request request = new Request.Builder()
                .url(ELASTIC_SEARCH_URL)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
                .build();
        Response response = client.newCall(request).execute();
        String res = response.body().string();

        return res;
    }
    private String buildBody(String crawlId, String text){
        return " {\r\n" +
                "  \"query\": {\r\n" +
                "    \"bool\": {\r\n" +
                "      \"must\": {\r\n" +
                "        \"bool\" : {\r\n" +
                "          \"must\": [\r\n" +
                "            { \"match\": { \"crawlId\": \"" + crawlId + "\" }} ,\r\n" +
                "            { \"match\": { \"content\": \"" + text + "\"}} \r\n" +
                "           ]\r\n" +
                "        }\r\n" +
                "      }\r\n" +
                "    }\r\n" +
                "  }\r\n" +
                "}";
    }
}