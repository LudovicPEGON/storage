package com.everteam.storage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everteam.storage.domain.ESChangeService;
import com.everteam.storage.domain.ESRepository;
import com.everteam.storage.drive.IDrive;
import com.everteam.storage.service.change.Change;
import com.everteam.storage.service.change.ChangeService;
import com.everteam.storage.service.change.Changes;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Component
public class RepositoryService {

    
    private final static Logger LOG = LoggerFactory
            .getLogger(RepositoryService.class);

    @Autowired
    BeanFactory beanFactory;
    
    

    public static Map<String, IDrive> drives = new HashMap<>();
    public static Map<String, Integer> schedulerCount = new HashMap<>();

    public IDrive getDrive(String id) {
        return drives.get(id);
    }

    public List<IDrive> getDriveList() {
        return new ArrayList<>(drives.values());

    }

    public List<ESRepository> getRepositoryList() throws Exception {
        List<ESRepository> repositories = new ArrayList<>();
        List<IDrive> drives = getDriveList();
        for (IDrive drive : drives) {
            ESRepository repository = drive.getRepository();
            ESRepository cloneRepository = new ESRepository()
                    .id(repository.getId()).name(repository.getName())
                    .rootDirectory(repository.getRootDirectory())
                    .type(repository.getType())
                    .clientSecret(repository.getClientSecret())
                    .clientId(repository.getClientId());

            // we need to clone repository, because they are static and their id
            // are modified by serializer.
            repositories.add(cloneRepository);
        }
        return repositories;
    }

    public void startRepository(ESRepository repository) {
        IDrive drive = drives.get(repository.getName());
        if (drive == null) {
            switch (repository.getType()) {
            case GOOGLE:
                drive = (IDrive) beanFactory.getBean("google");
                break;
            case FS:
                drive = (IDrive) beanFactory.getBean("fs");
                break;
            case ONEDRIVE:
                drive = (IDrive) beanFactory.getBean("onedrive");
                break;
            default:
                break;
            }
            if (drive != null) {
                try {
                    repository.setId(repository.getName());
                    drive.init(repository);

                    drives.put(repository.getName(), drive);
                    ESChangeService changeService = repository.getChangeService();
                    if (changeService != null) {
                        Integer delay = repository.getChangeService().getScheduler();
                        if (delay != null) {
                            schedulerCount.put(repository.getName(), delay);
                        }
                    }

                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                } catch (GeneralSecurityException e) {
                    LOG.error(e.getMessage(), e);
                }

            }
        }

    }



    @Scheduled(fixedDelay = 1000)
    public void checkUpdatesScheduled() throws GeneralSecurityException, IOException, SevereException, ExecutionException  {
        
            for (IDrive drive : getDriveList() ) {
                ESRepository repository = drive.getRepository();

                Integer count =  schedulerCount.get(repository.getName()); 
                if (count != null) {
                    if (count == repository.getChangeService().getScheduler()) {
                        count = 0;
                        try {
                            checkUpdates(drive, repository);
                        }
                        catch(SevereException e) {
                            
                        }
                    }
                    else {
                        count++;
                        schedulerCount.put(repository.getName(), count);
                    }
                }
            }
        
    }

    private void checkUpdates(IDrive drive, ESRepository repository)
            throws IOException, SevereException, JsonGenerationException, JsonMappingException, ExecutionException {
        ChangeService service = buildChangeService(repository);
        ArrayList<Object> result = new ArrayList<>();
        
            String token = getToken(drive);
            Changes changes = drive.getChanges(null, token, getMaxRow(service));
            LOG.info("CHECKUPDATES for " + repository.getName() + ". [" + changes.size() + "] founded." );
            List<Future<?>> futures = new ArrayList<>();
            for (Change change : changes.getItems()) {
                Future<?> future = service.process(change);
                futures.add(future);
            }
            
            do {
                try {
                    Iterator<Future<?>> it = futures.iterator();
                    while(it.hasNext()) {
                        Future<?> f = it.next();
                        if (f.isDone()) {
                            it.remove();
                            result.add(f.get());
                            if (result.size() ==100) {
                                service.push(result);
                                result.clear();
                                
                            }
                        }
                    }
                    Thread.sleep(10);
                }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof SevereException) {
                        /*Iterator<Future<?>> it = futures.iterator();
                        while(it.hasNext()) {
                            Future<?> f = it.next();
                            f.cancel(true);
                        }*/
                        throw (SevereException)e.getCause();
                        
                    }
                    else {
                        LOG.error(e.getMessage(), e);
                    }
                }
                catch (InterruptedException e) {
                    LOG.error(e.getMessage(), e);
                }
                
            }
            while (!futures.isEmpty());
            service.push(result);
            
            persistToken(drive, changes.getToken());
        
    }
    
    

    private void persistToken(IDrive drive, String token) throws JsonGenerationException, JsonMappingException, IOException {
        Path path = buildTargetPath(drive);
        Files.newOutputStream(path).write(token.getBytes());
    }

    private String getToken(IDrive drive) {
        String date = null;
        
        Path path = buildTargetPath(drive);
        if (Files.exists(path)) {
            try {
                date = new String(Files.readAllBytes(path));
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
        }
        if (date == null || date.isEmpty()) {
            date = OffsetDateTime.MIN.toString();
        }
        return date;
    }
    
    private Path buildTargetPath(IDrive drive) {
        Path targetDir = Paths.get(".", ".et-storage", drive.getRepository().getId());
        targetDir.toFile().mkdirs();
        Path target = targetDir.resolve("schedulerToken");
        return target;
    }
    
    
    

    

    public ChangeService buildChangeService(ESRepository repository) {
        ESChangeService service = repository.getChangeService();
        ChangeService changeservice = (ChangeService) beanFactory.getBean(service.getType());
        changeservice.init(repository, service);
        
        return changeservice;
    }

    public int getMaxRow (ChangeService service) {
        Integer maxrows = service.getMaxRows();
        if (maxrows == null) {
            maxrows = 1000;
        }
        return maxrows;
    }

}
