package com.everteam.storage.service.change;

import java.nio.file.Path;
import java.time.OffsetDateTime;

import com.everteam.storage.service.FSTree.Status;

public  class Change {
    String fileId ;
    Status type;
    Path path;
    boolean isDirectory;
    OffsetDateTime date;
    Path oldPath;

    public Change(String fileId, Path path, Status type, OffsetDateTime date) {
        super();
        this.fileId = fileId;
        this.type = type;
        this.path = path;
        this.date = date;
    }

    public Status getType() {
        return type;
    }

    
    

    public String getFileId() {
        return fileId;
    }

    public Path getPath() {
        return path;
    }

    public OffsetDateTime getDate() {
        return date;
    }

   

    public Path getOldPath() {
        return oldPath;
    }

    public void setOldPath(Path oldPath) {
        this.oldPath = oldPath;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }
    
    

}
