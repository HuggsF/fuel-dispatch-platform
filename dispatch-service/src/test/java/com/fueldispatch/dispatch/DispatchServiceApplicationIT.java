package com.fueldispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.dispatch.application.port.in.ChangeOrderStatusUseCase;
import com.fueldispatch.dispatch.application.port.in.CreateOrderUseCase;
import com.fueldispatch.dispatch.application.port.in.GetOrderQuery;
import com.fueldispatch.dispatch.application.port.in.ListOrdersQuery;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Smoke test for FND-1.5: the service starts and reports itself healthy. An integration test since
 * phase 02, because the context needs PostgreSQL.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DispatchServiceApplicationIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ApplicationContext context;

    @Test
    void actuatorHealth_whenApplicationStarts_returnsUp() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    // API-3.3: every use case is the same service behind the transactional proxy.
    @Test
    void useCases_areOneTransactionalServiceBean() {
        Object createOrder = context.getBean(CreateOrderUseCase.class);

        assertThat(AopUtils.isAopProxy(createOrder)).isTrue();
        assertThat(context.getBean(ChangeOrderStatusUseCase.class)).isSameAs(createOrder);
        assertThat(context.getBean(GetOrderQuery.class)).isSameAs(createOrder);
        assertThat(context.getBean(ListOrdersQuery.class)).isSameAs(createOrder);
    }

    @Test
    void clock_isUtc() {
        assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
