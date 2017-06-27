package com.everteam.storage.service.change;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import com.everteam.analytics.domain.Field;
import com.everteam.analytics.domain.InputDoc;
import com.everteam.analytics.domain.Updates;
import com.everteam.extractor.domain.TextData;
import com.everteam.storage.client.analytics.AnalyticsService;
import com.everteam.storage.client.extractor.TextService;
import com.everteam.storage.domain.ESChangeService;
import com.everteam.storage.domain.ESFile;
import com.everteam.storage.domain.ESPermission;
import com.everteam.storage.domain.ESRepository;
import com.everteam.storage.jackson.ESFileSerializer;
import com.everteam.storage.service.FileService;
import com.everteam.storage.service.SevereException;
import com.everteam.storage.utils.ESFileId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.client.ClientException;
import com.netflix.hystrix.exception.HystrixRuntimeException;

import feign.RetryableException;

@Component(value = "ANALYTICS")
@Scope("prototype")
public class ChangeServiceAnalytics implements ChangeService {
    Logger LOG = LoggerFactory.getLogger(this.getClass().getName());

    @Value("${storage.extractor:}")
    String textserviceType;
    
    @Autowired
    @Qualifier("client")
    TextService textclient;
    
    @Autowired
    FileService fileService;

    @Autowired
    ObjectMapper jacksonObjectMapper;

    
    @Autowired
    AnalyticsService analyticsService;

    @Autowired
    BeanFactory beanFactory;
    
    ESRepository repository;
    
    String collectionName;
    
    boolean extractor;
    ESChangeService definition;
    
   
    
    int count;
    
    
    private DateTimeFormatter dateTimeFormat = DateTimeFormatter.ISO_INSTANT;
    
    

    @Override
    public void init(ESRepository repository, ESChangeService definition) {
        this.definition = definition;
        this.extractor = true;
        for (Entry<String, String> entry : definition.getParams().entrySet()) {
            switch (entry.getKey()) {
                case "collection" :
                    collectionName = entry.getValue();
                    break;
                case "extractor" :
                    extractor = Boolean.parseBoolean(entry.getValue());
                    break;
                default :
                    break;
            }
        }
        this.repository = repository;
    }

    
    
    
    @Async
    @Override
    public Future<?> process(Change change) throws ExecutionException, SevereException {
        Result result = new Result(change);
        try {
            switch(change.getType()) {
                case add:
                    LOG.debug("############################# ADD :" + change.getFileId());
                    result.setDoc(addDocument(change));
                    break;
                case update:
                    LOG.debug("############################# UPDATE :" + change.getFileId());
                    result.setDoc(addDocument(change));
                    break;
                case delete:
                    LOG.debug("############################# DELETE : " + change.getFileId());
                    deleteDocuments(change);
                    break;
                case rename:
                    LOG.debug("############################# RENAME : " + change.getFileId());
                    renameDocuments(change);
                    break;
            }
        } 
        catch (HystrixRuntimeException | RetryableException e) {
            e.printStackTrace();
            throw new SevereException(e);
            
        }
        catch(ClosedByInterruptException e) {
            //OK just stopped by caller or time out
        }
        catch (Throwable e) {
            if (e.getCause() instanceof ClientException) {
                throw new SevereException(e.getCause());
            }
            else {
                OffsetDateTime odt = change.getDate();
                Path targetDir = Paths.get(".", ".et-storage").resolve(repository.getId())
                        .resolve("ANALYTICS")
                        .resolve("ERRORS")
                        .resolve(String.valueOf(odt.getYear()))
                        .resolve(String.valueOf(odt.getMonth()))
                        .resolve(String.valueOf(odt.getDayOfMonth()));
                targetDir.toFile().mkdirs();
                Path target = targetDir.resolve(change.getFileId());
                
                try {
                    jacksonObjectMapper.writeValue(Files.newOutputStream(target), change);
                } 
                catch(Throwable e1) {
                    throw new SevereException("Can't save change event : " + change.getFileId(), e1);
                }
                throw new ExecutionException(e);
            }
            
        } 
        return new AsyncResult<Result>(result);
    }

    
    private void renameDocuments(Change change) throws URISyntaxException, IOException {
        //HystrixCommandProperties.Setter().withExecutionTimeoutEnabled(false);
        if (!change.isDirectory()) {
            Updates update = new Updates();
            update.setQuery("cs_uid:\"" + buildDocId(change.getFileId())+"\"");
            update.setRows(1);
            
            update.putFieldsItem("fileName", change.getPath().getFileName().toString());
            analyticsService.updateDocs(collectionName, update);
        }
        else {
            Updates update = new Updates();
            Map<String , String> updatesFieldFolder =  new HashMap<>(); 
            Map<String , String> updatesFieldFolderPath =  new HashMap<>(); 
            
            List<Folder> oldfolders = getFolders(repository.getId(), change.getOldPath());
            Folder oldfolder  = oldfolders.get(oldfolders.size()-1);
            updatesFieldFolder.put("remove", oldfolder.getName());
            updatesFieldFolderPath.put("remove", oldfolder.getLevelPath());
            
            List<Folder> folders = getFolders(repository.getId(), change.getPath());
            Folder folder  = folders.get(folders.size()-1);
            updatesFieldFolder.put("add", folder.getName());
            updatesFieldFolderPath.put("add", folder.getLevelPath());
            
            String parent = folder.getPath();
            update.putFieldsItem("parent", parent);
          
                        
            Path path = Paths.get(repository.getId()).resolve(change.getOldPath());
            update.setQuery("folderPath:\"" + Folder.getLevelPath(path)+"\"");
            update.setRows(Integer.MAX_VALUE);
            update.putFieldsItem("folder", updatesFieldFolder);
            update.putFieldsItem("folderPath", updatesFieldFolderPath);
            
            analyticsService.updateDocs(collectionName, update);
            
        }
        
    }
    
    private void deleteDocuments(Change change) throws URISyntaxException, IOException {
        //HystrixCommandProperties.Setter().withExecutionTimeoutEnabled(false);
        if (!change.isDirectory()) {
            analyticsService.deleteDoc(collectionName, buildDocId(change.getFileId()));
        }
        else {
            Path path = Paths.get(repository.getId()).resolve(change.getPath());
            analyticsService.deleteByQuery(collectionName, "folderPath:\"" + Folder.getLevelPath(path)+"\""); 
        }
        
    }

    
    private ESFileId buildESFileId(String fileId) {
        return new ESFileId(repository.getId(), fileId);
    }
    
    private String buildDocId(ESFile esFile) throws URISyntaxException, IOException {
        return buildDocId(esFile.getId());
    }
    
    private String buildDocId(String fileId) throws URISyntaxException, IOException {
        return ESFileSerializer.serialize( repository.getId(), fileId);
    }
    
    
    
    private InputDoc addDocument(Change change) throws IOException, GeneralSecurityException, URISyntaxException {
        //HystrixCommandProperties.Setter().withExecutionTimeoutEnabled(false);
        
        InputDoc doc =  null;
        ESFile esFile = fileService.getFile(buildESFileId(change.getFileId()), true, true);
       
        
        if (!esFile.getDirectory()) {
            doc  =  indexFile(esFile, null, null);
            List<InputDoc> docs = new ArrayList<>(1);
            docs.add(doc);
            
            LOG.debug("INDEXFILE : " + change.getPath().toString());
            analyticsService.addDoc(collectionName, docs);
        }
        return doc;
    }
    
   
    
    
    
    private InputDoc indexFile(ESFile esFile,
            HashMap<String, String> fieldMap, HashMap<String, String> fieldTypeMap) throws GeneralSecurityException, URISyntaxException, IOException {
        InputDoc doc = new InputDoc(); 
       
            long startDate = new Date().getTime();
            int p = esFile.getName().lastIndexOf(".");
            String fileExt = esFile.getName().substring(p + 1);
            
            addField(doc, "cs_uid", buildDocId(esFile) );
            addField(doc, "cs_type", "fs");

            addField(doc, "fileName", esFile.getName());
            addField(doc, "fileType", fileExt);
            addField(doc, "fileSize", esFile.getFileSize());
            addField(doc, "repositoryId", esFile.getRepositoryId());
            
            List<String> paths = esFile.getParents().get(0).getPaths();
            Path parentPath = Paths.get("", paths.toArray(new String[paths.size()]));
            
            Path filePath = parentPath.resolve(esFile.getName());
            
            List<Folder> folders = getFolders(repository.getId(), parentPath);
            for (Folder folder: folders) {
                addField(doc, "folder", folder.getName());
                addField(doc, "folderPath", folder.getLevelPath());
            }
            String parent = folders.get(folders.size()-1).getPath();
            addField(doc, "parent", parent);
            
            
            
            addField(doc, "checksum", esFile.getChecksum());
            addField(doc, "lastModified", esFile.getLastModifiedTime().format(dateTimeFormat));
            addField(doc, "lastAccessed", esFile.getLastAccessTime().format(dateTimeFormat));
            
            List<ESPermission> permissions = esFile.getPermissions();
            if (permissions!= null) {
                for (ESPermission perm : esFile.getPermissions()) {
                    String fn = "cs_" + perm.getType().name().toLowerCase();
                    addField(doc, fn, perm.getAccountType() + "/" + perm.getUserId());
                }
            }
            
            if (extractor) {
                addTextAndMetadata(esFile, doc, fieldMap, fieldTypeMap);
            }
            

            LOG.debug("---> " + (System.currentTimeMillis() - startDate) + " : " + filePath.toString());
           
            
        
        
        
        return doc;
    }




    

    
    
    
    
    
    private List<Folder> getFolders(String repositoryId, Path parent) {
        List<Folder> folders = new ArrayList<>();
        
        Path path = Paths.get(repositoryId).resolve(parent);
         
        Path current = Paths.get("");
        for (int ii = 0; ii < path.getNameCount(); ii++) {
            Path p = path.getName(ii);
            Folder folder = new Folder();
            
            current = current.resolve(p);
            
            folder.setName(p.toString());
            folder.setPath(current);
           
            folders.add(folder);
        }
        return folders;
    }
    
    

   
    
    private static class Folder {
        String name;
        Path path;
        
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getPath() {
            return getPath(path);
        }
        public void setPath(Path path) {
            this.path = path;
        }
        public String getLevelPath() {
            return String.format("%02d", path.getNameCount()-1) + "-" + getPath(path);
        }
        
        public static String getLevelPath(Path path) {
            return String.format("%02d", path.getNameCount()-1) + "-" + getPath(path);
        }
        
        private static String getPath(Path path) {
            StringBuffer sbf = new StringBuffer(""); 
            for (int ii = 0; ii < path.getNameCount(); ii++) {
                if (sbf.length()>0) {
                    sbf.append("/");
                }
                sbf.append(path.getName(ii).toString());
            }
            return sbf.toString();
        }
        
        
        
        
    }
    
    
    

    
    private void addField(InputDoc doc, String name, Object value) {
        doc.addFieldsItem(new Field().name(name).value(value));
        
    }

    private TextData addTextAndMetadata(ESFile esfile, InputDoc doc, HashMap<String, String> fieldMap, HashMap<String, String> fieldTypeMap) throws IOException, GeneralSecurityException, URISyntaxException {
        //HystrixCommandProperties.Setter().withExecutionTimeoutEnabled(false);
       
       
        String lang = null;
        TextData data = null;
        Path path = null;
        try {
            path = Files.createTempFile("et-storage.", "." + FilenameUtils.getExtension(esfile.getName()));
            ESFileId fileId = new ESFileId(esfile.getRepositoryId(), esfile.getId());
            fileService.downloadTo(fileId, Files.newOutputStream(path));
            
            TextService textService = null;
            if (textserviceType != null && !textserviceType.isEmpty() ) {
                try {
                    textService = (TextService) beanFactory.getBean(textserviceType);
                } catch (BeansException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
            if (textService == null) {
                textService = textclient; 
            }
            
            data = textService.getText(path.toString());
            
            
            /*ESFileId fileId = new ESFileId(esfile.getRepositoryId(), esfile.getId());
            MultipartFileStorage mfs = new MultipartFileStorage(esfile.getName(), esfile.getMimeType(), fileService.getFileContent(fileId));
            data = textClient.getText(mfs);
            */
            
            

            String text = data.gettext();
            addField(doc, "text",text);
            lang = data.getMetadata().get("cs_lang");
            if (lang != null && ",fr,en,es,ar,".indexOf(lang) > -1) {
                addField(doc, "text_" + lang.toUpperCase(), text);
            }
        }
        finally {
            Files.delete(path);
        }
            /*for (Entry<String, String> entry: data.getMetadata().entrySet()) {
                addField(doc, entry, fieldMap, fieldTypeMap);
            }*/
            
        
        
  
        return data;
    }
    
    
    
    /*
     * 
     * private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    
    private SimpleDateFormat dateFormat2 = new SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy", Locale.ENGLISH);
     * 
     * private void addField(InputDoc doc, Entry<String, String> entry, HashMap<String, String> fieldMap, HashMap<String, String> fieldTypeMap) {
        String name = entry.getKey();
        String value = entry.getValue();
        if (value.trim().length() == 0) return;
        String fieldName = fieldMap.get(name);
        if (fieldName != null) {
            //if (!doc.containsKey(fieldName)) {
                if (fieldTypeMap.get(fieldName).equals("date")) {
                    if (value.length() == 10) {
                        value += "T00:00:00Z";
                    } else if (value.length() == 19) {
                        value += "Z";
                    } else if (value.length() == 28 || value.length() == 29) {
                        value = getDateString(value);
                    } else if (value.matches("\\d{4}-\\d{2}-\\d{2}.*") && value.length() > 20) {
                        value = value.substring(0,  19) + "Z";
                    }
                }
                addField(doc, fieldName, value);
            //}
        } else if (name.startsWith("cs_") || name.startsWith("custom") || name.startsWith("access_permission")) {
            name = name.replace(':', '_');
            name = name.replace(' ', '_');
            addField(doc, name, value);
        }
    }
    
    private String getDateString(String value) {
        String resp = null;
        try {
            Date date = dateFormat2.parse(value);
            resp = dateFormat.format(date);
        }
        catch (ParseException e) {
            resp = value;
        }
        return resp;
    }*/

    @Override
    public Integer getMaxRows() {
        return definition.getMaxrows();
    }

    @Override
    public void push(ArrayList<?> result) {
        /*List<InputDoc> docs = new ArrayList<>(result.size());
        for (Object object : result) {
            Result r =  (Result)object;
            LOG.debug(r.getChange().getPath().toString());
            docs.add(r.getDoc());
        }
        analyticsService.addDoc(collectionName, docs);
        LOG.info("#############INDEXATION : [" + result.size() + "] " + new Date().getTime());*/
    }
    
    
    public static class MultipartFileStorage implements MultipartFile {
        private final String name;


        private String contentType;

        private final byte[] content;
        
        /**
         * Create a new MockMultipartFile with the given content.
         * @param name the name of the file
         * @param content the content of the file
         */
        public MultipartFileStorage(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = (content != null ? content : new byte[0]);
        }
        
        
        @Override
        public byte[] getBytes() throws IOException {
            return this.content;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(this.content);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getOriginalFilename() {
            return this.name;
        }

        @Override
        public long getSize() {
            return this.content.length;
        }

        @Override
        public boolean isEmpty() {
            return (this.content.length == 0);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            FileCopyUtils.copy(this.content, dest);
            
        }
        
    }
    
    
    public static class Result {
        Change change;
        InputDoc doc;
        
        
        public Result(Change change) {
            super();
            this.change = change;
        }

        public Change getChange() {
            return change;
        }
       
        public InputDoc getDoc() {
            return doc;
        }
        public void setDoc(InputDoc doc) {
            this.doc = doc;
        }
        
        
        
    }

}
