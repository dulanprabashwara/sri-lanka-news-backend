package lk.srilankannews;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lk.srilankannews.config.RequestCorrelationFilter;
import lk.srilankannews.article.ArticleRepository;
import lk.srilankannews.source.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "news.ai.gemini.api-key=test-gemini-key",
        "news.ai.gemini.model=gemini-test",
        "news.ai.gemini.prompt-version=v1",
        "news.ai.gemini.timeout=5s",
        "news.ai.gemini.max-input-characters=30000",
        "ingestion.api-key=test-ingestion-key",
        "management.health.mongo.enabled=false",
        "management.health.redis.enabled=false",
        "news.processing.redis.enabled=false",
        "spring.data.redis.url=redis://localhost:6379",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class SriLankaNewsApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceRepository sourceRepository;

    @MockitoBean
    private ArticleRepository articleRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void actuatorHealthExposesOnlySafeInformation() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestCorrelationFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
