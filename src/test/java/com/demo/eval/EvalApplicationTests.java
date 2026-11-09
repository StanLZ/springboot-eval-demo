package com.demo.eval;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文冒烟测试：确认依赖注入、配置绑定和线程池 Bean 正常。
 */
@SpringBootTest(properties = "eval.rag-base-url=http://localhost:19999")
class EvalApplicationTests {

    @Test
    void contextLoads() {
    }
}
