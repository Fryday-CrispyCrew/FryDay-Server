package basakan.fryday.common.config;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.common.security.AdminApiKeyAuthenticationFilter;
import basakan.fryday.common.security.AdminJwtAuthenticationFilter;
import basakan.fryday.common.security.AdminJwtTokenProvider;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.UserActivityFilter;
import basakan.fryday.repository.admin.AdminAccountRepository;
import basakan.fryday.service.user.UserActivityRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(DocsSecurityProperties.class)
public class SecurityConfig {

    private static final String DOCS_PATH_PATTERN = "/docs/**";
    private static final String DOCS_ROLE = "DOCS";
    private static final String ADMIN_PATH_PATTERN = "/api/admin/**";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserActivityRecorder userActivityRecorder;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminAccountRepository adminAccountRepository;
    private final ObjectMapper objectMapper;

    @Value("${admin.api-key:#{null}}")
    private String adminApiKey;

    @Bean
    @Order(1)
    public SecurityFilterChain docsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(DOCS_PATH_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasRole(DOCS_ROLE)
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    // 앱 유저 체인과 분리해서, 앱 토큰 인증과 일별 활동 기록이 어드민 요청에 끼어들지 않게 한다
    @Bean
    @Order(2)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(ADMIN_PATH_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/admin/auth/login").permitAll()
                        .anyRequest().hasRole("ADMIN")
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(adminAuthenticationEntryPoint())
                )
                .addFilterBefore(new AdminJwtAuthenticationFilter(adminJwtTokenProvider, adminAccountRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AdminApiKeyAuthenticationFilter(adminApiKey), AdminJwtAuthenticationFilter.class);

        return http.build();
    }

    // 웹이 401 을 받으면 로그인 화면으로 보낼 수 있도록 공통 응답 형식으로 내려준다
    private AuthenticationEntryPoint adminAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            ErrorCode errorCode = ErrorCode.ADMIN_UNAUTHORIZED;
            response.setStatus(errorCode.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode.getMessage()));
        };
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // ASYNC는 SSE 응답이 완료·타임아웃될 때 컨테이너가 태우는 내부 dispatch다.
                        // JWT 필터가 건너뛰어 인증이 없는 상태이지만, 최초 REQUEST에서 이미 인가가 끝났으므로 허용한다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/users/social/login").permitAll()
                        .requestMatchers("/api/users/apple/login").permitAll()
                        .requestMatchers("/api/users/token/refresh").permitAll()
                        .requestMatchers("/api/users/nickname/check").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new UserActivityFilter(userActivityRecorder), JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager docsUserDetailsService(
            DocsSecurityProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        if (!StringUtils.hasText(properties.username()) || !StringUtils.hasText(properties.password())) {
            throw new IllegalStateException(
                    "REST Docs 인증 계정이 설정되지 않았습니다. 환경변수 DOCS_USER, DOCS_PASSWORD 를 지정해주세요."
            );
        }

        UserDetails docsUser = User.builder()
                .username(properties.username())
                .password(passwordEncoder.encode(properties.password()))
                .roles(DOCS_ROLE)
                .build();

        return new InMemoryUserDetailsManager(docsUser);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // TODO: 프로덕션 환경에서는 실제 도메인으로 변경 필요
        // configuration.setAllowedOrigins(List.of("https://yourdomain.com"));

        // 개발 환경: 모든 Origin 허용 (React Native는 CORS 영향 없음)
        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // preflight 캐시 1시간

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
