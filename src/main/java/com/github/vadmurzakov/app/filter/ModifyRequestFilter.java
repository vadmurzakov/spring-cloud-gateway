package com.github.vadmurzakov.app.filter;

import com.github.vadmurzakov.app.config.ModifyAbstractRequestFilter;
import com.github.vadmurzakov.app.entity.NewDto;
import com.github.vadmurzakov.app.entity.OldDto;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ModifyRequestFilter extends ModifyAbstractRequestFilter {

    public ModifyRequestFilter() {
        super(mapFunction());
    }

    /**
     * Функция преобразования старого типа данных в новый.
     */
    private static RewriteFunction<OldDto, NewDto> mapFunction() {
        return (serverWebExchange, oldDto) -> {
            var newDto = new NewDto(oldDto.getName(), oldDto.getAge());
            return Mono.just(newDto);
        };
    }
}
