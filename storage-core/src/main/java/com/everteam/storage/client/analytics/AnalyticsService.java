package com.everteam.storage.client.analytics;

import com.everteam.storage.client.AuthorizedFeignClient;

@AuthorizedFeignClient(name = "analytics", url= "#{'${everteam.gateway.url:}'.isEmpty()?'':'${everteam.gateway.url:}' +'/analytics'}")
public interface AnalyticsService extends com.everteam.analytics.client.AnalyticsClient {

}
