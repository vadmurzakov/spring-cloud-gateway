package com.github.vadmurzakov.app.config;

import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.GatewayToStringStyler;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class ModifyAbstractRequestFilter
    extends AbstractGatewayFilterFactory<ModifyAbstractRequestFilter.Config> {
    private final RewriteFunction rewriteFunction;
    private final List<HttpMessageReader<?>> messageReaders;

    public ModifyAbstractRequestFilter(final RewriteFunction rewriteFunction) {
        super(Config.class);
        this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
        this.rewriteFunction = rewriteFunction;
    }

    public GatewayFilter apply(final Config config) {
        return new GatewayFilter() {
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                var inClass = config.getInClass();
                var serverRequest = ServerRequest.create(exchange, ModifyAbstractRequestFilter.this.messageReaders);
                var modifiedBody = serverRequest.bodyToMono(inClass)
                    .flatMap((originalBody) -> rewriteFunction.apply(exchange, originalBody))
                    .switchIfEmpty(Mono.defer(() -> (Mono) rewriteFunction.apply(exchange, (Object) null)));
                var bodyInserter = BodyInserters.fromPublisher(modifiedBody, config.getOutClass());
                var headers = new HttpHeaders();
                headers.putAll(exchange.getRequest().getHeaders());
                headers.remove("Content-Length");
                if (config.getContentType() != null) {
                    headers.set("Content-Type", config.getContentType());
                }

                var outputMessage = new CachedBodyOutputMessage(exchange, headers);
                return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
                    var decorator = ModifyAbstractRequestFilter.this.decorate(exchange, headers, outputMessage);
                    return chain.filter(exchange.mutate().request(decorator).build());
                })).onErrorResume((throwable) -> Mono.error((Throwable) throwable));
            }

            public String toString() {
                return GatewayToStringStyler.filterToStringCreator(ModifyAbstractRequestFilter.this)
                    .append("Content type", config.getContentType()).append("In class", config.getInClass())
                    .append("Out class", config.getOutClass()).toString();
            }
        };
    }

    ServerHttpRequestDecorator decorate(ServerWebExchange exchange, final HttpHeaders headers,
                                        final CachedBodyOutputMessage outputMessage) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            public HttpHeaders getHeaders() {
                long contentLength = headers.getContentLength();
                var httpHeaders = new HttpHeaders();
                httpHeaders.putAll(headers);
                if (contentLength > 0L) {
                    httpHeaders.setContentLength(contentLength);
                } else {
                    httpHeaders.set("Transfer-Encoding", "chunked");
                }

                return httpHeaders;
            }

            public Flux<DataBuffer> getBody() {
                return outputMessage.getBody();
            }
        };
    }

    @Data
    public static class Config {
        private Class inClass;
        private Class outClass;
        private String contentType;
    }

}
