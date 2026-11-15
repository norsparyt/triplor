package com.triplor.adventure;

import com.triplor.testsupport.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class AdventureApplicationTests {

    @Test
    void contextLoads() {
    }
}
