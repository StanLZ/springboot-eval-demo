package com.demo.eval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RAG 评测平台入口。
 * 启动后访问 http://localhost:8082 打开评测页面。
 */
@SpringBootApplication
public class EvalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvalApplication.class, args);
    }
}
