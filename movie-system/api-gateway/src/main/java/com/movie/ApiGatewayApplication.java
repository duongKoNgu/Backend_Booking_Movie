package com.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    // CHỈ GIỮ LẠI DUY NHẤT BEAN NÀY
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Lấy token từ header
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // 1. Nếu đã đăng nhập (Có Token), dùng chính Token làm chìa khóa để phân biệt User
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return Mono.just(authHeader);
            }

            // 2. Nếu chưa đăng nhập (Khách vãng lai), tự động fallback về chặn theo địa chỉ IP thiết bị
            return Mono.just(
                    Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress()
            );
        };
    }
}

@RestController
class FallbackController {

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<Map<String, String>>> fallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error",   "service_unavailable",
                        "message", "Dịch vụ tạm thời không khả dụng. Vui lòng thử lại sau.",
                        "code",    "503"
                )));
    }
}