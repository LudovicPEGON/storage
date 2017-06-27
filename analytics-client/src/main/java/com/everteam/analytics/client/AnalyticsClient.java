package com.everteam.analytics.client;

import java.util.List;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.everteam.analytics.domain.InputDoc;
import com.everteam.analytics.domain.Updates;


@FeignClient(name = "analytics")
public interface AnalyticsClient {

    @RequestMapping(value = "api/collections/{collection}/docs/delete", produces = {
            "application/json" }, method = RequestMethod.POST)
    void deleteByQuery(@PathVariable("collection") String collection,
            @RequestParam(value = "q", required = true) String q);

    @RequestMapping(value = "api/collections/{collection}/docs/{docId}", produces = {
            "application/json" }, method = RequestMethod.DELETE)
    void deleteDoc(@PathVariable("collection") String collection, @PathVariable("docId") String docId);

    @RequestMapping(value = "api/collections/{collection}/docs", produces = {
            "application/json" }, method = RequestMethod.POST)
    void addDoc(@PathVariable("collection") String collection, @RequestBody List<InputDoc> body);

    @RequestMapping(value = "api/collections/{collection}/docs/update", produces = {
            "application/json" }, method = RequestMethod.PUT)
    void updateDocs(@PathVariable("collection") String collection, @RequestBody Updates update);

}
