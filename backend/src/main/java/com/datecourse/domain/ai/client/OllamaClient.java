package com.datecourse.domain.ai.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 안의 Ollama (포트폴리오 챗봇과 같은 것, GTX 1650 4GB). 모델: 답은 gemma3:4b, 임베딩은 embeddinggemma.
 * 사용자 글은 밖으로 나가지 않는다. 실패하면 null·빈 목록을 돌려주고 부른 쪽이 일반 검색으로 넘어간다.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient client;
    private final JsonMapper json;
    private final String chatModel;
    private final String embedModel;
    private final boolean enabled;

    public OllamaClient(@Value("${date-course.ai.ollama-url:http://127.0.0.1:11434}") String baseUrl,
                        @Value("${date-course.ai.chat-model:gemma3:4b}") String chatModel,
                        @Value("${date-course.ai.embed-model:embeddinggemma}") String embedModel,
                        @Value("${date-course.ai.enabled:true}") boolean enabled,
                        @Value("${date-course.ai.timeout-seconds:45}") int timeoutSeconds,
                        JsonMapper json) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.json = json;
        this.chatModel = chatModel;
        this.embedModel = embedModel;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public String embedModel() {
        return embedModel;
    }

    /** JSON 으로만 답하게 해서 그 글을 읽는다 (온도 0: 같은 질문에 같은 답). 실패하면 null */
    public JsonNode generateJson(String prompt, int maxTokens) {
        if (!enabled) {
            return null;
        }
        try {
            String body = client.post().uri("/api/generate").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", chatModel, "prompt", prompt, "stream", false, "format", "json", "keep_alive", "10m",
                            "options", Map.of("temperature", 0, "num_predict", maxTokens)))
                    .retrieve().body(String.class);
            String response = json.readTree(body).path("response").asString("");
            return response.isBlank() ? null : json.readTree(response);
        } catch (RuntimeException e) {
            log.warn("Ollama 답 만들기 실패: {}", e.getMessage());
            return null;
        }
    }

    /** 글 여러 개 → 정규화한 벡터 (내적 = 코사인). 실패하면 빈 목록 */
    public List<float[]> embed(List<String> texts) {
        if (!enabled || texts.isEmpty()) {
            return List.of();
        }
        try {
            String body = client.post().uri("/api/embed").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", embedModel, "input", texts, "keep_alive", "10m"))
                    .retrieve().body(String.class);
            List<float[]> out = new ArrayList<>();
            for (JsonNode v : json.readTree(body).path("embeddings")) {
                float[] vec = new float[v.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = (float) v.get(i).asDouble();
                }
                out.add(normalize(vec));
            }
            return out.size() == texts.size() ? out : List.of();
        } catch (RuntimeException e) {
            log.warn("Ollama 임베딩 실패: {}", e.getMessage());
            return List.of();
        }
    }

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return v;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }
}
