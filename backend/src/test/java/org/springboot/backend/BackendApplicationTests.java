package org.springboot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"repomedic.data-dir=target/context-data", "repomedic.ai-api-url=",
        "repomedic.ai-api-key=", "repomedic.ai-model=", "repomedic.sandbox-enabled=false"})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
