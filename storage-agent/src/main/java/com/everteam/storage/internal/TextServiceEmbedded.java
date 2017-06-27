package com.everteam.storage.internal;

import org.springframework.stereotype.Component;

@Component(value = "embedded")
public class TextServiceEmbedded extends com.everteam.extractor.service.TextService
        implements com.everteam.storage.client.extractor.TextService {
}
