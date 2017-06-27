package com.everteam.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties("everteam.gateway.client")
public class ClientCredentialsResourceDetailsImpl  extends ClientCredentialsResourceDetails {

    
     
}




