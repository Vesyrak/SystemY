package com.bonkers;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;

/**
 * Multicast communication Thread.
 */
public class MulticastCommunicator extends Thread {
    //https://docs.oracle.com/javase/7/docs/api/java/net/MulticastSocket.html
    /**
     * Multicast address of the group to join.
     */
    private final String castAdress = "234.5.6.7";

    /**
     *  InetAddress of the group.
     */
    private InetAddress group = null;

    /**
     * Boolean of the finished thread.
     */
    private Boolean IsFinished = false;

    /**
     * Multicastsocket of the thread.
     */
    private MulticastSocket castSocket = null;
    /**
     * QueueEvent of packages that are received.
     * You can subscribe to its events.
     */
    public QueueEvent<Tuple<String,String>> packetQueue=new QueueEvent<Tuple<String,String>>();

    /**
     * Constructor that automatically joins the multicast group.
     */
    public MulticastCommunicator() {
        joinGroup();
    }

    /**
     * Main server loop, runs and gets multicasts and adds them to the QueueEvent.
     * No interaction required.
     */
    public void run(){
        while(!IsFinished) {
            Tuple<String,String> info = receiveMulticast();
            if(info!=null)
                packetQueue.add(info);
        }
    }

    /**
     * Join group function. Connects to the multicast group.
     */
    private void joinGroup() {
        try {
            group = InetAddress.getByName(castAdress);
            castSocket = new MulticastSocket(6789);
            castSocket.joinGroup(group);
        } catch (Exception e) {
        }
    }

    /**
     * Sends a string over multicast.
     * @param msg Message to send.
     */
    public void sendMulticast(String msg) {
        try {
            DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), group, 6789);
            castSocket.send(packet);
        } catch (Exception e) {
        }
    }

    /**
     * Receives a multicast packet.
     *
     * @return returns a Tuple containing its information as string and its IP address as InetAddress
     */
    public Tuple<String,String> receiveMulticast() {
        try {
            byte[] buf = new byte[2048];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            castSocket.receive(recv);
            System.out.println("packet received" + recv.getData());
            String address=recv.getAddress().getHostAddress();
            System.out.println(address);
            String Nodename=new String(buf,0,recv.getLength());
            System.out.println(Nodename);
            return new Tuple<String,String>(Nodename,address);
        } catch (Exception e) {
            IsFinished=true;
            System.out.println(e);
        }
        return null;
    }

    /**
     * Leave Multicast group.
     */
    public void leaveGroup() {
        try {
            castSocket.leaveGroup(group);
        } catch (Exception e) {
        }
    }

}