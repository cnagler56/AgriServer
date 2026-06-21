package com.home.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Global CORS as a servlet filter.
 *
 * Per-controller {@code @CrossOrigin} only adds headers on the handler dispatch,
 * so error responses (404/401/403/500) — which Spring forwards internally to
 * /error — come back without a CORS header and the browser reports a misleading
 * "No 'Access-Control-Allow-Origin'" failure instead of the real status.
 *
 * A {@link CorsFilter} registered for REQUEST + ERROR + ASYNC dispatches applies
 * the headers to every response, including those error dispatches. The existing
 * {@code @CrossOrigin} annotations become harmless no-ops (Spring's CORS
 * processor skips when the header is already present).
 */
@Configuration
public class CorsConfig {

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.setAllowedOrigins(List.of("http://localhost:3000"));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
		return bean;
	}
}
