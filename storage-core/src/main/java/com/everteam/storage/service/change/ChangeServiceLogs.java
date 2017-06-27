package com.everteam.storage.service.change;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import com.everteam.storage.domain.ESChangeService;
import com.everteam.storage.domain.ESRepository;
import com.everteam.storage.service.FileService;
import com.everteam.storage.service.SevereException;

@Component(value = "LOGS")
@Scope("prototype")
public class ChangeServiceLogs implements ChangeService {
    Logger LOG = LoggerFactory.getLogger(this.getClass().getName());

    @Autowired
    FileService fileService;

    ChangeService changeService;

    ESRepository repository;

    ESChangeService definition;
    
    @Override
    public void init(ESRepository repository, ESChangeService definition) {
        this.definition = definition;
        this.repository = repository;
    }

    @Async
    @Override
    public Future<?> process(Change change) throws ExecutionException, SevereException {
        try {
            switch(change.getType()) {
                case add:
                    LOG.debug("############################# ADD :" + change.getPath() + "[" +  change.getFileId()+ "]");
                    break;
                case update:
                    LOG.debug("############################# UPDATE : " + change.getPath() + "[" + change.getFileId() + "]");
                    /*file = fileService.getFile(new ESFileId(repository.getId(), change.getFileId()), false, false);
                    Path targetDir =
                            Paths.get("/home/local/tmp").resolve(file.getRepositoryId())
                            .resolve(String.valueOf(file.getLastModifiedTime().getYear()))
                            .resolve(String.valueOf(file.getLastModifiedTime().getMonth()))
                            .resolve(String.valueOf(file.getLastModifiedTime().getDayOfMonth()));

                    targetDir.toFile().mkdirs();

                    Path target =
                            targetDir.resolve(ESFileSerializer.serialize(file.getRepositoryId(), file.getId()));

                    jacksonObjectMapper.writeValue(target.toFile(), file);
                    break;*/
                    break;
                case delete:
                    LOG.debug("############################# DELETE : " + change.getPath() + "["  + change.getFileId() + "]");
                    break;
                case rename:
                    LOG.debug("############################# RENAME : " + change.getOldPath() + " --> " + change.getPath() + "["  +change.getFileId() + "]");
                    break;
            }
            
            
            
            
            
        } 
        catch (Throwable e) {
            throw new ExecutionException(e);
        }
        return new AsyncResult<Change>(change);
    }
    
    @Override
    public Integer getMaxRows() {
        return definition.getMaxrows();
    }

    @Override
    public void push(ArrayList<?> result) {
        // TODO Auto-generated method stub
        
    }

}
