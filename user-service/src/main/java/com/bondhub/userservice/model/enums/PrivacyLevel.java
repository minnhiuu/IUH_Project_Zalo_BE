package com.bondhub.userservice.model.enums;

import lombok.Getter;

@Getter
public enum PrivacyLevel {
    EVERYBODY(""),
    FRIENDS(""),
    PRIVATE(""),
    CONTACTED("")
    ;

    private final String content;

    PrivacyLevel(String content){
        this.content = content;
    }

}
