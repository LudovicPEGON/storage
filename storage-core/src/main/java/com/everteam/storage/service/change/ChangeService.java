package com.everteam.storage.service.change;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.everteam.storage.domain.ESChangeService;
import com.everteam.storage.domain.ESRepository;
import com.everteam.storage.service.SevereException;

public interface ChangeService {
    Future<?> process(Change change) throws ExecutionException, SevereException;

    void init(ESRepository repository, ESChangeService definition);
    
    Integer getMaxRows();

    void push(ArrayList<?> result);
}
