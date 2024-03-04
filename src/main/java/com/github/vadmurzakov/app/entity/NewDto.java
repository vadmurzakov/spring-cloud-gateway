package com.github.vadmurzakov.app.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class NewDto {

    User user;

    public NewDto(String name, String age) {
        this.user = new User(name, Long.valueOf(age));
    }

    @Data
    @AllArgsConstructor
    public static class User {
        String name;
        Long age;
    }

}
