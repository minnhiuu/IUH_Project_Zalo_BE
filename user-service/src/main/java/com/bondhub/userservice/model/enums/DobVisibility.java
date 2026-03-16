package com.bondhub.userservice.model.enums;

import lombok.Getter;

@Getter
public enum DobVisibility {
    HIDDEN("Hidden - Do not show date of birth"),
    FULL_DATE("Show full date (day, month, year)"),
    MONTH_DAY_ONLY("Show only month and day")
    ;

    private final String description;

    DobVisibility(String description){
        this.description = description;
    }

}
