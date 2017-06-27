package com.everteam.storage.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.everteam.storage.domain.ESRepository;
import com.everteam.storage.drive.FSDrive;
import com.everteam.storage.service.FSTree.ESFileTree;
import com.everteam.storage.service.change.Change;
import com.everteam.storage.service.change.Changes;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;

@Component
@Scope("prototype")
public class FSTree {

    private static final String WATCHER = "watcher";
    private static final String SYNCHRONIZATION_DELAY = "synchroDelay";
    
    
    private static final Logger LOG = LoggerFactory.getLogger(FSTree.class);




    private FSDrive drive;
    ESFileTree root;
    PathMatcher matcher;
    
    int watchID;
    boolean started;

    int synchroCount;
    int schedulerRows;
    
    @Autowired
    RepositoryService repositoryService;

    @Autowired
    private ObjectMapper jacksonObjectMapper;
    

    
    
    public ESFileTree getRoot() {
        return root;
    }

    
    
    public void init(FSDrive drive) throws IOException {
        this.started = false;
        this.drive = drive;
        
        
        String patterns = drive.getRepository().getPatterns();
        if (patterns != null && !patterns.isEmpty())  {
            patterns = ".{" + patterns  + "}";
        }
        else {
            patterns = "";
        }
        
        
        matcher = FileSystems.getDefault().getPathMatcher("glob:**" + patterns );
        
        
       
        
        try {
            if (Files.exists(buildTargetPath())) {
                root = jacksonObjectMapper.readValue(Files.newInputStream(buildTargetPath()), ESFileTree.class);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        if (root == null) {
            root = new ESFileTree();
        }
        ESRepository repository = drive.getRepository(); 
        if (Boolean.parseBoolean(repository.getParams().get(WATCHER))) {;
            startRecursiveWatcher();
        }
               
        
        synchroCount = getIntParam(SYNCHRONIZATION_DELAY, Integer.MAX_VALUE);
        
    }

    private void startRecursiveWatcher() throws JNotifyException {
        String path = drive.getRootPath().toString();
        int mask = JNotify.FILE_CREATED | JNotify.FILE_DELETED | JNotify.FILE_MODIFIED | JNotify.FILE_RENAMED;
        boolean watchSubtree = true;
        watchID = JNotify.addWatch(path, mask, watchSubtree, new JNotifyListener() {
            @Override
            public void fileRenamed(int wd, String rootPath, String oldName, String newName) {
                LOG.debug("##FileRenamed : wd #" + wd + " root = " + rootPath + ", " + oldName + " -> " + newName);
                
                Path oldpath = Paths.get(rootPath, oldName);
                Path newPath = Paths.get(rootPath, newName);
                
                Path oldPathRelative = buildRelativePath(oldpath);
                Path newPathRelative = buildRelativePath(newPath);
                if (Files.isDirectory(newPath) || (matcher.matches(oldpath) &&  matcher.matches(newPath))) {
                    ESFileTree oldtree = root.remove(oldPathRelative);
                    if (oldtree != null) {
                        
                        
                        root.add(newPathRelative, oldtree);
    
                        String oldRelativeName = "";
                        for (int i=0; i<oldPathRelative.getNameCount(); i++) {
                            if (!oldRelativeName.isEmpty()) {
                                oldRelativeName += "/";
                            }
                            oldRelativeName += oldPathRelative.getName(i);
                        }
                        oldtree.getMetadata().addRenaming(oldRelativeName);
                    }
                }
                else if (matcher.matches(oldpath)) {
                    ESFileMetada fileInfos = root.get(oldPathRelative);
                    fileInfos.setDeleted(OffsetDateTime.now());
                }
                else if (matcher.matches(newPath)) {
                    ESFileMetada fileInfos = root.get(newPathRelative);
                    fileInfos.setCreated(OffsetDateTime.now());
                    fillSize(fileInfos, newPath);
                }
            }

            @Override
            public void fileModified(int wd, String rootPath, String name) {
                LOG.debug("##FileModified : wd #" + wd + " root = " + rootPath + ", " + name);
                
                Path path = Paths.get(rootPath, name);
                if (matcher.matches(path)) {
                    if (!Files.isDirectory(path)) {
                        ESFileMetada fileInfos = root.get(buildRelativePath(path));
                        fileInfos.setUpdated(OffsetDateTime.now());
                        fillSize(fileInfos, path);
                    }
                }
            }

           

            @Override
            public void fileDeleted(int wd, String rootPath, String name) {
                LOG.debug("##FileDeleted : wd #" + wd + " root = " + rootPath + ", " + name);
                
                Path path = Paths.get(rootPath, name);
                ESFileMetada fileInfos = root.get(buildRelativePath(path), false);
                if (fileInfos != null) {
                    fileInfos.setDeleted(OffsetDateTime.now());
                }
            }

            @Override
            public void fileCreated(int wd, String rootPath, String name) {
                LOG.debug("##FileCreated : wd #" + wd + " root = " + rootPath + ", " + name);
                Path path = Paths.get(rootPath, name);
                if (Files.isDirectory(path) || matcher.matches(path)) {
                    ESFileMetada fileInfos = root.get(buildRelativePath(path));
                    fileInfos.setCreated(OffsetDateTime.now());
                    fillSize(fileInfos, path);
                }
            }

            private Path buildRelativePath(Path path) {
                Path rp = null;
                try {
                    rp = drive.buildRelativePath(path);
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                }
                return rp;
            }

            

        });

    }
    
    
    
    private static void fillSize(ESFileMetada fileInfos, Path path) {
        try {
            
            if (!Files.isDirectory(path)) {
                fileInfos.setSize(Files.size(path));
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static OffsetDateTime getLastModifiedTime(BasicFileAttributes attrs) {
        OffsetDateTime lastDate;
        FileTime lastModifiedTime = attrs.lastModifiedTime();
        FileTime creationTime = attrs.creationTime();
        if (creationTime.compareTo(lastModifiedTime)>0) {
            lastModifiedTime = creationTime;
        }
        lastDate = OffsetDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneOffset.UTC);
        return lastDate;
    }

    
    /*public void persistScheduled() throws IOException {
        if (persistenceCount != null) {
            if (persistenceCount == getIntParam(TREE_MEMORY_PERSISTENCE)) {
                persistenceCount = 0;
                persist();
            }
            else {
                persistenceCount++;
            }
        }
    }*/
    
    @Scheduled( fixedDelay = 10000)
    public void persist() throws IOException, JsonGenerationException, JsonMappingException {
        jacksonObjectMapper.writeValue(buildTargetPath().toFile(), root);
    }



    private Integer getIntParam(String paramName, int defaultValue) {
        Integer paramValue = null;
        String sparamValue = drive.getRepository().getParams().get(paramName);
        if (sparamValue != null) {
            paramValue =  Integer.parseInt(drive.getRepository().getParams().get(paramName));
        }
        else {
            paramValue =  defaultValue;
        }
        return paramValue;
    }

    @Scheduled(fixedDelay = 1000)
    public void synchroScheduled() throws IOException {
            if (synchroCount == getIntParam(SYNCHRONIZATION_DELAY, Integer.MAX_VALUE)) {
                synchroCount = 0;
                synchro();
            }
            else {
                synchroCount++;
            }
    }

    private void  synchro() throws IOException, JsonGenerationException, JsonMappingException {
        Date startDate = new Date();
        LOG.info("started ExecuteUpdates thread " + Thread.currentThread().getId() + " at "
                + dateFormat.format(startDate));
        Path start = drive.getRootPath();
        final ESFileTree tmpRoot = new ESFileTree();
        
        final CountFiles cf = new CountFiles();
        
        
        /*System.out.println(authorityRepository.count());
        long startTime = new Date().getTime(); 
        for (int i=0; i<1; i++ ) {
            List<Authority> as = new ArrayList<>();
            for (int j=0; j<100; j++ ) {
                
                Authority authority = new Authority();
                authority.setId(UUID.randomUUID());
                authority.setName("lpe" + i);
                as.add(authority);
            }
            authorityRepository.save(as);
            authorityRepository.flush();
            as.clear();
        }*/
       
        
//        for (int i=0; i<100000; i++ ) {
//                Authority authority = new Authority();
//                authority.setId(UUID.randomUUID());
//                authority.setName("lpe" + i);
//                authorityRepository.saveAndFlush(authority);
//        }
        /*if (i%100 ==0) {
            authorityRepository.flush();
        }*/
        
        
        
        
      // System.out.println(new Date().getTime() - startTime);    
                
                
    
        
        /*
        try (Stream<Path> paths = Files.walk(start)) {
            
            paths.forEach(new Consumer<Path>() {
                
                @Override
                public void accept(Path t) {
                    try {
                        
                        
                        Path path = drive.buildRelativePath(t);

                        if (!path.toString().isEmpty()) {
                           ESFileMetada fileInfos = new ESFileMetada();
                            try {
                                BasicFileAttributes attrs = Files.readAttributes(t, BasicFileAttributes.class);
    
                                fileInfos.setUpdated(getLastModifiedTime(attrs));
                            } catch (IOException e) {
                                LOG.error(e.getMessage(), e);
                            }
                            boolean isdirectory = true;
                            if (!Files.isDirectory(t)) {
                                fileInfos.setSize(Files.size(t));
                                isdirectory = false;
                            }
                            cf.inc(isdirectory);
                            
                            //if (path.toString().endsWith(".java") || isdirectory) {
                                
                                tmpRoot.add(path, fileInfos);
                            //}
                        }
                        
                        
                    } catch (Throwable e) {
                        LOG.error(e.getMessage(), e);
                    }

                }
            });
            
            
        }*/
        
        
        
        
        Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new FileVisitor<Path>() {

            @Override
            public FileVisitResult postVisitDirectory(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
                add(file, attrs);
                
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                add(file, attrs);
                
                return FileVisitResult.CONTINUE;
            }

            private void add(Path file, BasicFileAttributes attrs) throws IOException {
                Path path = drive.buildRelativePath(file);

                boolean isDirectory = attrs.isDirectory();
                
                
                if (isDirectory) {
                    if (!path.toString().isEmpty()) {
                        tmpRoot.add(path, buildMetada(attrs));
                        cf.inc(true);
                    }
                }
                else {
                    cf.inc(false);
                    if (matcher.matches(path)) {
                        tmpRoot.add(path, buildMetada(attrs));
                        cf.incMatchFiles();
                    }
                }
                        
                
                
            }
            
            private ESFileMetada buildMetada(BasicFileAttributes attrs) throws IOException  {
                ESFileMetada metadata = new ESFileMetada();
                if (!attrs.isDirectory()) {
                    metadata.setSize(attrs.size());
                }
                metadata.setUpdated(getLastModifiedTime(attrs));
                return metadata;
            }

            @Override
            public FileVisitResult visitFileFailed(Path filecas, IOException exc) throws IOException {
                return FileVisitResult.SKIP_SUBTREE;
            }

        });
        
        
        
        
        cf.end();

        SyncItems syncItems = new SyncItems();
        for (ESFileFlat fileInfos : root.list()) {
            syncItems.setTarget(fileInfos);
        }
        for (ESFileFlat fileInfos : tmpRoot.list()) {
            syncItems.setSource(fileInfos);
        }
        if (compareFiles(syncItems)) {
            applyChanges(syncItems);
        }
        started = true;
        persist();

        LOG.info("ended thread " + Thread.currentThread().getId() + " started at " + dateFormat.format(startDate)
        + " finished at " + dateFormat.format(new Date()));
    }
    
    public static class CountFiles {
        
        
        long countFiles = 0; 
        long matchFiles = 0; 
        long countDirectory =0;
        Date startdate;
        
        public CountFiles() {
            super();
            startdate = new Date();
        }

        public void incMatchFiles() {
            matchFiles++;
            
        }


        public void inc(boolean isDirectory) {
            if (isDirectory) {
                if (countDirectory>0 && countDirectory%10000 == 0) {
                    LOG.info("Parsing progress... [" + countDirectory + "] directories have been parsed");
                }
                countDirectory++;
            }
            else {
                if (countFiles > 0 && countFiles%10000 == 0) {
                    LOG.info("Parsing progress... [" + countFiles + "] files have been parsed");
                }
                countFiles++;
            }
            
        }

        public void end() {
            long diffInMillies = new Date().getTime() - startdate.getTime();
            long timeInseconds =  TimeUnit.SECONDS.convert(diffInMillies,TimeUnit.MILLISECONDS);
            
            LOG.info("Parsing end. [" + countDirectory + "] directories and ["  + matchFiles + "/" + countFiles  + "] files have been found in [" + timeInseconds + "] seconds");
            
        }
        
        
    }

    @PreDestroy
    public void cleanup() {
        try {
            JNotify.removeWatch(watchID);
        } catch (IOException e) {
            LOG.error("Error closing watcher service", e);
        }
    }

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    private void applyChanges(SyncItems syncItems) {
        List<SyncItem> updates = new ArrayList<>();
        for (SyncItem syncItem : syncItems.values()) {
            Status diffStatus = syncItem.getDiffStatus();
            if (diffStatus != null) {
                updates.add(syncItem);
            }
        }
        /*updates.sort(new Comparator<SyncItem>() {
            @Override
            public int compare(SyncItem o1, SyncItem o2) {
                ESFileMetada mds1 = o1.getSource();
                ESFileMetada mds2 = o2.getSource();
                if (mds1 == null) {
                    return 1; 
                }
                else if (mds2 == null) {
                    return -1;
                }
                else {


                    return mds1.getDate().compareTo(mds2.getDate());
                }
            }
        });*/

        
        
        
        for (SyncItem syncItem : updates) {
            Status diffStatus = syncItem.getDiffStatus();

            switch (diffStatus) {
            case add:
                root.add(Paths.get(syncItem.getId()), syncItem.getSource());
                break;
            case delete:
                syncItem.getTarget().setDeleted(OffsetDateTime.now());
                break;
            case update:
                syncItem.getTarget().setUpdated(syncItem.getSource().getDate());
                break;
            default:
                break;
            }
        }
    }

    private Path buildTargetPath() {
        Path targetDir = Paths.get(".", ".et-storage", drive.getRepository().getId());
        targetDir.toFile().mkdirs();
        Path target = targetDir.resolve("tree");
        return target;
    }

    private boolean compareFiles(SyncItems syncItems) {
        boolean differencesFound = false;
        for (SyncItem item : syncItems.values()) {
            Status diffStatus = compareItem(item);
            item.setDiffStatus(diffStatus);
            if (diffStatus != null) {
                differencesFound = true;
            }
        }
        return differencesFound;
    }

    // Returns null if source and target are equal.
    private Status compareItem(SyncItem item) {
        ESFileMetada sInfos = item.getSource();
        ESFileMetada tInfos = item.getTarget();

        Status diffStatus = null;

        if (tInfos == null) {
            diffStatus = Status.add;
        } else if (sInfos == null) {
            diffStatus = Status.delete;
        } else {
            // source is a file
            if (sInfos.getSize() != null) {
                if (!sInfos.getSize().equals(tInfos.getSize())) {
                    diffStatus = Status.update;
                }
                if (!sInfos.getDate().equals(tInfos.getDate())) {
                    diffStatus = Status.update;
                }
            }
            // source is folder
            else if (tInfos.getSize() != null) {
                diffStatus = Status.update;
            }

        }
        return diffStatus;
    }

    public static class ESFileTree {
        @JsonProperty("md")
        ESFileMetada metadata;
        @JsonProperty("c")
        Map<String, ESFileTree> children;

        public ESFileTree() {
            super();
            metadata = new ESFileMetada();
        }

       

        public ESFileTree get(String id) {
            ESFileTree tree= null;
            if (children!= null) {
                tree = children.get(id);
            }
            return tree;
        }

        public ESFileMetada getMetadata() {
            return metadata;
        }

        public void setMetadata(ESFileMetada metadata) {
            this.metadata = metadata;
        }

        public void setChildren(Map<String, ESFileTree> children) {
            this.children = children;
        }

        public List<ESFileFlat> listAll() {
            List<ESFileFlat> all = list(null, null, true);
            return all;
        }
        
        
        public List<ESFileFlat> list() {
            List<ESFileFlat> all = list(null, Status.delete, true);
            return all;
        }
        
        public List<ESFileFlat> children() {
            return list(null, Status.delete, false);
        }
        
        
        public Path getPath(String id) {
            return getPath(Paths.get(""), id);
        }
        
        
        private Path getPath(Path path, String id) {
            Path pathId = null;
            if (!path.toString().isEmpty()) {
                if (getMetadata().getId().equals(id)) {
                    pathId =  path;
                }
            }
            
            if (pathId== null && children!= null) {
                for (Entry<String, ESFileTree> entry : children.entrySet()) {
                    ESFileTree child = entry.getValue();
                    pathId = child.getPath(path.resolve(entry.getKey()), id);
                    if (pathId != null) {
                        break;
                    }
                }
            }
            return pathId;
        }

        private List<ESFileFlat> list(Path path, Status status, boolean recursive) {
            List<ESFileFlat> files = new ArrayList<>();
            if (path == null) {
                path = Paths.get("");
            }
            if (children!= null) {
                for (Entry<String, ESFileTree> entry : children.entrySet()) {
                    ESFileTree child = entry.getValue();
                    if (recursive) {
                        files.addAll(child.list(path.resolve(entry.getKey()), status, recursive));
                    }
                    
                    Status s = child.getMetadata().getLastAction();
                    if (s == null) {
                        s = Status.update;
                    }
                    if (status == null ||  s == null ||!s.equals(status)) {
                        files.add(new ESFileFlat(path.resolve(entry.getKey()), child.getMetadata()));
                    }
                    
                    //files.addAll(entry.getValue().list(path.resolve(entry.getKey()), status));
                }
            }
            return files;
        }

        public Map<String, ESFileTree> getChildren() {
            return children;
        }

        public void add(Path path, ESFileMetada metadata) {
            ESFileTree tree = search(path, true);
            tree.setMetadata(metadata);
        }
        
        public void add(Path path, ESFileTree tree) {
            Path parent = path.getParent();
            ESFileTree treeParent = this;
            if (parent != null) {
                treeParent = search(path.getParent(), true);
            }
            treeParent.put(path.getFileName().toString(), tree);
        }
        
       
        public ESFileMetada get(Path path) {
            return get(path, true); 
        }
        
        public ESFileMetada get(Path path, boolean create) {
            ESFileTree tree = search(path, create);
            return tree.getMetadata();
        }

        public ESFileTree remove(Path path) {
            ESFileTree subtree =  getSubTree(path.getParent());
            ESFileTree removed = null;
            if (subtree != null) {
                removed = subtree.getChildren().remove(path.getFileName().toString());
            }
            return removed;
        }

        public ESFileTree getSubTree(Path path) {
            ESFileTree current = this;
            if (path!= null && !path.toString().isEmpty()) {
                for (int i = 0; i < path.getNameCount(); i++) {
                    String id = path.getName(i).toString();
    
                    ESFileTree fi = current.get(id);
                    current = fi;
                    if (current == null) {
                        break;
                    }
                }
            }
            return current;
        }

        private void put(String key, ESFileTree tree) {
            if (children == null) {
                children = new ConcurrentHashMap<>();
            }
            children.put(key, tree);
        }

        private ESFileTree search(Path path, boolean create) {
            ESFileTree current = this;
            for (int i = 0; i < path.getNameCount(); i++) {
                String id = path.getName(i).toString();
                ESFileTree fi = current.get(id);
                if (fi == null) {
                    if (!create) {
                        break;
                    }
                    fi = new ESFileTree();
                    current.put(id, fi);
                    
                }
                current = fi;
            }
            return current;
        }

    }

    public static class Renaming {
        @JsonProperty("n")
        String name;
        @JsonProperty("d")
        OffsetDateTime date;



        public Renaming() {
            super();
        }

        public Renaming(String name, OffsetDateTime date) {
            super();
            this.name = name;
            this.date = date;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public OffsetDateTime getDate() {
            return date;
        }

        public void setDate(OffsetDateTime date) {
            this.date = date;
        }

    }

    public static class ESFileFlat {
        Path path;
      
        ESFileMetada metadata;

        public ESFileFlat(Path path, ESFileMetada metadata) {
            super();
            this.path = path;
            this.metadata = metadata;
        }

       

        public Path getPath() {
            return path;
        }



        public void setPath(Path path) {
            this.path = path;
        }



        public ESFileMetada date() {
            return metadata;
        }

        public void setMetadata(ESFileMetada metadata) {
            this.metadata = metadata;
        }

        public ESFileMetada getMetadata() {
            return metadata;
        }
        
        

    }

    public static class ESFileMetada {
        @JsonProperty("id")
        String id;
        @JsonProperty("s")
        Long size;
        @JsonProperty("a")
        Status lastAction;
        @JsonProperty("d")
        OffsetDateTime date;
        @JsonProperty("r")
        List<Renaming> renamings;

        
        
        public ESFileMetada() {
            super();
            this.id = String.valueOf(UUID.randomUUID());
        }
        
        

        public String getId() {
            return id;
        }



        public OffsetDateTime getDate() {
            return date;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public Status getLastAction() {
            return lastAction;
        }

        public void setLastAction(Status lastAction) {
            this.lastAction = lastAction;
        }

        public void setDate(OffsetDateTime date) {
            this.date = date;
        }

        public void setUpdated(OffsetDateTime updated) {
            lastAction = Status.update;
            this.date = updated;
        }

        public void setDeleted(OffsetDateTime deleted) {
            lastAction = Status.delete;
            this.date = deleted;
        }

        public void setCreated(OffsetDateTime created) {
            lastAction = Status.add;
            this.date = created;

        }

        public List<Renaming> getRenamings() {
            return renamings;
        }

        public void setRenamings(List<Renaming> renamings) {
            this.renamings = renamings;
        }

        public void addRenaming(String oldname) {
            if (this.renamings == null) {
                this.renamings = new ArrayList<>();
            }
            this.renamings.add(new Renaming(oldname, OffsetDateTime.now()));
        }

    }

    public static enum Action {
        @JsonProperty("C")
        created, 
        @JsonProperty("U")
        updated, 
        @JsonProperty("D")
        deleted;

    }

    public static class SyncItems extends HashMap<String, SyncItem> {

        public void setSource(ESFileFlat fileflat) {
            set(fileflat, true);
        }

        public void setTarget(ESFileFlat fileflat) {
            set(fileflat, false);

        }

        public void set(ESFileFlat fileflat, boolean isSource) {
            ESFileMetada fileInfos = fileflat.getMetadata();
            if (!Action.deleted.equals(fileInfos.getLastAction())) {
                String key = fileflat.getPath().toString();
                SyncItem item = get(key);
                if (item == null) {
                    item = new SyncItem(key);
                    put(key, item);
                }
                if (isSource) {
                    item.setSource(fileInfos);
                } else {
                    item.setTarget(fileInfos);
                }
            }
        }

    }

    public static class SyncItem {

        ESFileMetada source;
        ESFileMetada target;
        String id;
        Status diffStatus;

        public SyncItem() {
            super();
        }

        public void setDiffStatus(Status diffStatus) {
            this.diffStatus = diffStatus;
        }

        public SyncItem(String id) {
            this.id = id;
        }

        public void setSource(ESFileMetada source) {
            this.source = source;

        }

        public void setTarget(ESFileMetada target) {
            this.target = target;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ESFileMetada getSource() {
            return source;
        }

        public ESFileMetada getTarget() {
            return target;
        }

        public Status getDiffStatus() {
            return diffStatus;
        }

    }

    public static enum Status {
        @JsonProperty("C")
        add, // add to target - source exists, target does not exist
        @JsonProperty("U")
        update, // modify target - source and target exist but are
        // different
        @JsonProperty("R")
        rename, // rename target - source and target exist and are equal,
        // but file name upper/lower case characters differ
        @JsonProperty("D")
        delete; // delete from target - source does not exist, target
        // exists


    }


    public Changes getChanges(Path start, OffsetDateTime date, int maxrows) {
        
        
        
        
        
        
        Changes changes = new Changes(date.toString());
        
        if (started) {
            /*List<Authority> authorities = authorityRepository.findAll(new Sort(Sort.Direction.ASC, "date"));
            
            authorities.stream().limit(1000).forEach(new Consumer<Authority>() {

                @Override
                public void accept(Authority t) {
                    LOG.debug(t.getDate().toString());
                    
                }
            });
            */
            
            
            
            List<ESFileFlat> files = root.getSubTree(start).listAll();
            files.forEach(new Consumer<ESFileFlat>() {
                @Override 
                public void accept(ESFileFlat t) {
                    //LOG.debug("check file : " + t.getId());
                    OffsetDateTime currentDate = t.getMetadata().getDate();
    
                    if( currentDate!= null && currentDate.isAfter(date)) {
                        switch (t.getMetadata().getLastAction()) {
                        case add:
                        case update :
                        case delete :
                            //LOG.debug("file modified : " + t.getId() + " action : " + t.getMetadata().getLastAction());
                            Change change = new Change(t.getMetadata().getId(), t.getPath(), t.getMetadata().lastAction, currentDate);
                            change.setDirectory(t.getMetadata().getSize() == null);
                            changes.add(change);
                            
                           
                        default:
                            break;
                        }
    
                    }
    
                    List<Renaming> renamings = t.getMetadata().getRenamings();
                    if (renamings!= null) {
                        Renaming from = null;
                        for(Renaming renaming : renamings) {
                            if (from== null && renaming.getDate().isAfter(date)) {
                                from = renaming;
                                break;
                            }
                        }
                        if (from != null) {
                            //LOG.debug("file renamed : " + from.getName() + " --> " + t.getId());
                            
                            Change change = new Change(t.getMetadata().getId(), t.getPath(), Status.rename, from.getDate());
                            change.setOldPath(Paths.get(from.getName()));
                            change.setDirectory(t.getMetadata().getSize() == null);
                            changes.add(change);
                            
                        }
                    }
                }
    
            });
            
            changes.limit(maxrows);
            
            OffsetDateTime maxDate =  null;
            for (Change change : changes.getItems()) {
                if (maxDate == null || maxDate.isBefore(change.getDate())) {
                    maxDate = change.getDate();
                }
            }
            if (maxDate!= null) {
                changes.setToken(maxDate.toString());
            }
            
            
            
        }
        return changes;
    }


    

}
