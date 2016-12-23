package com.bonkers;

import jdk.nashorn.internal.codegen.CompilerConstants;

import java.io.File;
import java.io.Serializable;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * TODO Jente
 */
public class AgentFileList implements  Serializable {
    public HashMap<File, Boolean> FileMap = new HashMap<>();
    public List<File> Filelist = null;

    public Boolean started = false;

    private static AgentFileList instance = null;


    protected AgentFileList() {}

    public List<File> Update(List<File> List){
        Filelist = List;
        started=true;
        getAndUpdateCurrentNodeFiles();
        checkLockRequests();
        checkUnlock();
        Filelist = new LinkedList<>();
        FileMap.forEach(((file, aBoolean) -> {
            Filelist.add(file);
        }));
        return Filelist;
    }

    /**
     * Singleton make instance if none exists else make one
     * @return instance
     */
    public static AgentFileList getInstance() {
        if(instance == null) {
            instance = new AgentFileList();
        }
        return instance;
    }

    private Client client = null;

    public void setClient(Client client)
    {
        this.client = client;
    }

    public Client getClient() { return client; }

    /**
     * Get the files of the node the agent runs on and check if files already exist or not
     */
    private void getAndUpdateCurrentNodeFiles(){
        if(client.fm.ownedFiles != null)
        {
            if(client.fm.ownedFiles.size() > 0)
            {
                client.fm.ownedFiles.forEach((fileInfo) -> {
                    FileMap.putIfAbsent(new File(fileInfo.fileName), false);
                });
            }
        }
        System.out.println(client.fm.ownedFiles.size() + " " + FileMap.size());
    }
    private void checkLockRequests(){
        client.LockQueue.forEach((fileName) -> {
            if(!FileMap.replace(fileName, false, true))
            {
                client.LockStatusQueue.add(new Tuple<>(fileName, false));
            }
            else
            {
                client.LockStatusQueue.add(new Tuple<>(fileName, true));
            }
        });
    }
    private void checkUnlock(){
        client.UnlockQueue.forEach((fileName) ->{
            FileMap.replace(fileName, true, false);
        });
    }
}
