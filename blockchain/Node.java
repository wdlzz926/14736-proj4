package blockchain;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;

import com.google.gson.Gson;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import message.*;

public class Node {

    private HttpServer node_skeleton;
    protected Gson gson;
    private List<Integer> ports;
    private int nodeId;
    private List<Block> id_chain;
    private List<Block> vote_chain;

    // private class Block {
    //     int id;
    //     Map<String, String> map;
    //     long timestamp;
    //     long nonce;
    //     String prehash;
    //     String hash;

    //     // public block(int id, Map<String, String> map, Timestamp timestamp, int nonce,
    //     // String prehash, String hash) {
    //     public Block(int id, String hash) {
    //         this.id = id;
    //         // this.map = map;
    //         this.timestamp = System.currentTimeMillis();
    //         // this.nonce = nonce;
    //         this.prehash = hash;
    //         // this.hash = hash;
    //     }

    //     public void setHash(String string) throws NoSuchAlgorithmException {
    //         MessageDigest digest = MessageDigest.getInstance("SHA-256");
    //         byte[] hash = digest.digest(string.getBytes(StandardCharsets.UTF_8));
    //         BigInteger number = new BigInteger(1, hash);

    //         // Convert message digest into hex value
    //         StringBuilder hexString = new StringBuilder(number.toString(16));

    //         // Pad with leading zeros
    //         while (hexString.length() < 32) {
    //             hexString.insert(0, '0');
    //         }

    //         this.hash = hexString.toString();
    //     }
    // }

    Node(String node_id, String port_list) {
        nodeId = Integer.parseInt(node_id);
        List<String> portstr = Arrays.asList(port_list.split(","));
        ports = new ArrayList<>();
        for (String port : portstr) {
            ports.add(Integer.parseInt(port));
        }
        this.gson = new Gson();
        try {
            this.node_skeleton = HttpServer.create(new InetSocketAddress(ports.get(nodeId)), 0);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.node_skeleton.setExecutor(Executors.newCachedThreadPool());

        this.add_node_api();
        this.id_chain.add(new Block());
        this.vote_chain.add(new Block());
        this.node_skeleton.start();
    }

    private void add_node_api(){
        this.getBlockChain();
        this.node_skeleton.createContext("/getchain", (exchange -> {

        }));
    }

    private void getBlockChain(){

    }
    public static void main(String[] args) throws Exception{
        if (args.length != 2) throw new Exception("Need 2 args: <index id> <port list> ");
        FileOutputStream f = new FileOutputStream("node.log",true);
        System.setOut(new PrintStream(f));
        Node node = new Node(args[0], args[1]);
//        node.start();
    }


}
