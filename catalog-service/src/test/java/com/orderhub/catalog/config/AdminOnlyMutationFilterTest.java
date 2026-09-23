package com.orderhub.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The gateway-trusted RBAC check described in ARCHITECTURE.md's "Gateway-trusted identity"
 * section: api-gateway validates the JWT and forwards {@code X-User-Role}; this filter is the
 * only thing standing between an authenticated-but-non-admin caller and a product mutation.
 */
@DisplayName("AdminOnlyMutationFilter")
class AdminOnlyMutationFilterTest {

    private final AdminOnlyMutationFilter filter = new AdminOnlyMutationFilter(new ObjectMapper());

    @Nested
    @DisplayName("mutating a product")
    class Mutations {

        @Test
        @DisplayName("POST without X-User-Role is rejected with 403 and never reaches the chain")
        void shouldRejectPostWithoutRoleHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/products");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("ADMIN");
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("POST with a non-ADMIN role is rejected with 403")
        void shouldRejectPostForNonAdminRole() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/products");
            request.addHeader("X-User-Role", "CUSTOMER");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("POST with X-User-Role: ADMIN reaches the chain")
        void shouldAllowPostForAdmin() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/products");
            request.addHeader("X-User-Role", "ADMIN");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("PUT without X-User-Role is rejected with 403")
        void shouldRejectPutWithoutRoleHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/products/" + java.util.UUID.randomUUID());
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("DELETE without X-User-Role is rejected with 403")
        void shouldRejectDeleteWithoutRoleHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/products/" + java.util.UUID.randomUUID());
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("PATCH is rejected without ADMIN even though no PATCH route exists yet — deny by default")
        void shouldRejectPatchWithoutRoleHeader() throws Exception {
            // Pins the fail-closed design: any verb outside GET/HEAD/OPTIONS needs ADMIN, so a
            // future PATCH endpoint is protected the day it is added, not forgotten.
            MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/products/" + java.util.UUID.randomUUID());
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            verify(chain, never()).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("reading products")
    class Reads {

        @Test
        @DisplayName("GET never requires the role header")
        void shouldAllowGetWithoutRoleHeader() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("paths outside the product catalog")
    class OtherPaths {

        @Test
        @DisplayName("a POST to an unrelated path is never blocked by this filter")
        void shouldNotScopeToOtherPaths() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }
}
