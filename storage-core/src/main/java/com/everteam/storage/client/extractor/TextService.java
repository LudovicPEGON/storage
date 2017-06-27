package com.everteam.storage.client.extractor;

import org.springframework.beans.factory.annotation.Qualifier;

import com.everteam.storage.client.AuthorizedFeignClient;

@AuthorizedFeignClient(name = "extractor", 
url= "#{'${everteam.gateway.url:}'.isEmpty()?'':'${everteam.gateway.url:}' +'/extractor'}")
@Qualifier(value="client")

public interface TextService extends com.everteam.extractor.client.TextClient {}
