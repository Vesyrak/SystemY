package com.bonkers;

import java.io.File;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * It is he, FileManager, Manager of Files, Replicator of objects!
 */
public class FileManager implements QueueListener{
    /**
     * The Downloadqueue for downloading files
     */
    public QueueEvent<Tuple<String,String>> downloadQueue;
    /**
     * The location to download to
     */
    private final File downloadLocation;
    /**
     * A Map containing local files and the nodes who are owner of them
     */
    private Map<String,NodeInfo> localFiles;
    /**
     * A List of the files this node owns
     */
    private List<FileInfo>ownedFiles;
    /**
     * This nodes' ID
     */
    private NodeInfo id;
    /**
     * The file checker, used for checking local file updates
     */
    private FileChecker fileChecker;
    /**
     * Server connection interface
     */
    private ServerIntf server;

    /**
     * The constructor, sets up the basic file list
     * @param downloadLocation The location of the files
     * @param server The server interface
     * @param id The id of this node
     * @param prevId The id of the previous node, used for replication purposes. NOTE THAT THIS ISNT CONNECTED PERMANENTLY. That is because the prevId might change, and I'm too lazy for an addition interface
     */
    public FileManager(File downloadLocation, ServerIntf server, NodeInfo id, NodeInfo prevId){
        this.downloadLocation =downloadLocation;
        this.id=id;
        this.server=server;
        downloadQueue=new QueueEvent<>();
        downloadQueue.addListener(this);
        fileChecker=new FileChecker(downloadLocation);
        localFiles=fileChecker.checkFiles(id);
        ownedFiles=new ArrayList<>();
        for (Map.Entry<String,NodeInfo> file: localFiles.entrySet()) {
            FileInfo f=new FileInfo();
            f.fileName=file.getKey();
            f.fileOwners=new ArrayList<>();
            f.fileOwners.add(file.getValue());
        }
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Map<NodeInfo,String> l=fileChecker.checkFiles(id, localFiles);
                for(String file: l.values()){
                    if(!localFiles.containsKey(file)){
                       Replicate(file,prevId);
                    }
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    /**
     * First replication when the node starts
     * @param prevId Previous node id
     */
    public void StartupReplication(NodeInfo prevId){
        for(Map.Entry<String,NodeInfo> file:localFiles.entrySet()){
            Replicate(file.getKey(),prevId);

        }
    }

    /**
     * Replicates specified file to either the previd or the location the nameserver says
     * @param filename the name of the file
     * @param prevId the id of the previous node
     */
    private void Replicate(String filename,NodeInfo prevId){
        try {
            String ip = server.findLocationFile(filename);
            if (Objects.equals(id.Address, ip))
                RequestDownload(prevId.Address, filename);
            else {
                RequestDownload(ip, filename);
                for (FileInfo file:ownedFiles) {//Todo this can be optimized
                   if(Objects.equals(file.fileName, filename)){
                        setOwnerFile(file);
                        break;
                   }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Rechecks ownership of files, this gets run when a nextNeighbor gets added
     * @param next NodeInfo of the next neighbor
     */
    public void RecheckOwnership(NodeInfo next){
        for(Map.Entry<String,NodeInfo> file:localFiles.entrySet()){
            int localHash=HashTableCreator.createHash(file.getKey());
            if(localHash<=id.Hash)
                System.out.println("File will stay");
            else if(localHash<=next.Hash) {
                System.out.println("File will be relocated");
                RequestDownload(next.Address, file.getKey());
                for (FileInfo fileInfo:ownedFiles) {//Todo this can be optimized
                    if (Objects.equals(fileInfo.fileName, file.getKey())) {
                        setOwnerFile(fileInfo);
                        break;
                    }
                }
            }
            else
                System.out.println("Dere be krakenz here");
        }
    }

    /**
     * Sends a download request of a file to another node
     * @param ip Ip address of the node
     * @param file file to download
     */
    private void RequestDownload(String ip, String file){
        try {
            Registry registry = LocateRegistry.getRegistry(ip);
            NodeIntf node = (NodeIntf) registry.lookup("NodeIntf");
            node.requestDownload(id, file);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void queueFilled() {
        Tuple<String,String> data=downloadQueue.poll();
        new Thread(new TCPClient(data.x,data.y,downloadLocation)).start();
    }

    /**
     * Checks for the owner of the files TODO unused?
     * @param nextNode
     * @return
     */
    public List<String> CheckIfOwner( NodeInfo nextNode)
    {
        List<String> OwnerOfList = new ArrayList<>();
        Map<String,NodeInfo> fileMap = fileChecker.checkFiles(id, localFiles);
        List<String> fileList=new ArrayList(fileMap.keySet());
        fileList.listIterator().forEachRemaining((file)->{
            int fileHash = HashTableCreator.createHash(file);
            if(fileHash > id.Hash)
            {
                if(fileHash < nextNode.Hash)
                {
                    OwnerOfList.add(file);
                }
            }
        });
        return OwnerOfList;
    }

    /**
     * Sets the ownership of a file, gets called via RMI
     * @param file
     */
    public void setOwnerFile(FileInfo file) {
        file.fileOwners.add(id);
        ownedFiles.add(file);
    }
}
