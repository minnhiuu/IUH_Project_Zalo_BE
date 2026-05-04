package com.bondhub.searchservice.config;

import com.bondhub.searchservice.enums.MessageSearchSection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MessageSearchSectionConverter implements Converter<String, MessageSearchSection> {

    @Override
    public MessageSearchSection convert(String source) {
        return MessageSearchSection.fromValue(source);
    }
}
