package de.coldtea.verborum.msdictionary;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires the local Postgres from docker-compose.yml — re-enable once a DB is available in CI/dev")
@SpringBootTest
class MsDictionaryApplicationTests {

    @Test
    void contextLoads() {
    }

}
