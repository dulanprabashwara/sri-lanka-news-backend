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
        "ingestion.api-key=test-ingestion-key",
        "management.health.mongo.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
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
