package basakan.fryday.controller.group;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.domain.group.FryGroup;
import basakan.fryday.service.group.GroupSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import basakan.fryday.repository.group.FryGroupRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.springframework.mock.web.MockAsyncContext;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GroupEventStreamController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@Import({GroupSseService.class, SimpleMeterRegistry.class})
@DisplayName("그룹 이벤트 스트림 API")
class GroupEventStreamControllerTest extends RestDocsSupport {

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 1L;

    @Autowired private GroupSseService groupSseService;
    @Autowired private MeterRegistry meterRegistry;
    @MockitoBean private FryGroupRepository fryGroupRepository;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
    }

    @Test
    @DisplayName("그룹 이벤트 스트림 연결 API")
    void streamGroupEvents() throws Exception {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(mock(FryGroup.class)));

        // when
        MvcResult result = mockMvc.perform(get("/api/groups/{groupId}/events", GROUP_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andDo(document("group-events",
                        pathParameters(parameterWithName("groupId").description("그룹 ID"))))
                .andReturn();

        groupSseService.broadcast(GROUP_ID, 2L);

        // then
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:connected");
        assertThat(body).contains("event:group-progress");
        assertThat(body).contains("{\"memberUserId\":2}");
    }

    @Test
    @DisplayName("같은 그룹의 여러 연결이 모두 수신하고, 끊긴 연결은 제거된다")
    void broadcastToAllConnections() throws Exception {
        // given - MockMvc 연결 1개 + 직접 연결 후 종료한 연결 1개
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(mock(FryGroup.class)));

        MvcResult first = mockMvc.perform(get("/api/groups/{groupId}/events", GROUP_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        SseEmitter closed = groupSseService.connect(GROUP_ID, USER_ID);
        closed.complete();

        // when - 끊긴 연결은 조용히 제거되고 나머지는 정상 수신
        groupSseService.broadcast(GROUP_ID, 3L);
        groupSseService.broadcast(GROUP_ID, 4L);

        // then
        String body = first.getResponse().getContentAsString();
        assertThat(body).contains("{\"memberUserId\":3}");
        assertThat(body).contains("{\"memberUserId\":4}");
    }

    @Test
    @DisplayName("실패 - 그룹원이 아니면 404")
    void streamGroupEvents_notMember() throws Exception {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/groups/{groupId}/events", GROUP_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("지표 - 연결이 끊긴 사유별로 한 번씩만 집계된다")
    void disconnectMetrics() throws Exception {
        // given
        given(fryGroupRepository.findByIdAndMemberUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(mock(FryGroup.class)));
        double completion = disconnects("completion");
        double timeout = disconnects("timeout");
        double error = disconnects("error");
        double connections = connections();

        MockAsyncContext completed = openConnection();
        MockAsyncContext timedOut = openConnection();
        MockAsyncContext failed = openConnection();
        assertThat(connections()).isEqualTo(connections + 3);

        // when - 서블릿 컨테이너처럼 timeout·error 뒤에도 complete를 알린다
        for (AsyncListener listener : timedOut.getListeners()) {
            listener.onTimeout(new AsyncEvent(timedOut));
        }
        for (AsyncListener listener : failed.getListeners()) {
            listener.onError(new AsyncEvent(failed, new IOException("broken pipe")));
        }
        for (MockAsyncContext context : new MockAsyncContext[]{completed, timedOut, failed}) {
            for (AsyncListener listener : context.getListeners()) {
                listener.onComplete(new AsyncEvent(context));
            }
        }

        // then
        assertThat(connections()).isEqualTo(connections);
        assertThat(disconnects("completion")).isEqualTo(completion + 1);
        assertThat(disconnects("timeout")).isEqualTo(timeout + 1);
        assertThat(disconnects("error")).isEqualTo(error + 1);
    }


    private MockAsyncContext openConnection() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/groups/{groupId}/events", GROUP_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        return (MockAsyncContext) result.getRequest().getAsyncContext();
    }


    private double connections() {
        return meterRegistry.get("group.sse.connections").gauge().value();
    }

    private double disconnects(String reason) {
        return meterRegistry.get("group.sse.disconnects").tag("reason", reason).counter().count();
    }
}
