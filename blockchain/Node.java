package blockchain;

import java.io.*;
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

import lib.MessageSender;
import message.*;


public class Node {
    protected static final String HOST_URI = "http://127.0.0.1:";
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";
    protected static final String BROADCAST_URI = "/broadcast";

    private HttpServer node_skeleton;
    protected Gson gson;
    private List<Integer> ports;
    private int nodeId;
    private LinkedList<Block> id_chain = new LinkedList<Block>();
    private LinkedList<Block> vote_chain = new LinkedList<Block>();
    private MessageSender messageSender = new MessageSender(1);
    private Boolean sleep = false;

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

    private void add_node_api() {
        this.getBlockChain();
        this.mineBlock();
        this.addBlock();
        this.broadcast();
        this.sleep();
    }

    private void sleep() {
        this.node_skeleton.createContext("/sleep", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                SleepRequest sleepRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    sleepRequest = gson.fromJson(isr, SleepRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int timeout = sleepRequest.getTimeout();
                StatusReply statusReply = new StatusReply(true, "");
                respText = gson.toJson(statusReply);
                this.sleep = true;
                this.generateResponseAndClose(exchange, respText, returnCode);
                try {
                    Thread.sleep(1000 * timeout);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                this.sleep = false;
            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
                this.generateResponseAndClose(exchange, respText, returnCode);
            }

        }));
    }

    private void getBlockChain() {
        this.node_skeleton.createContext("/getchain", (exchange -> {
            String respText = "";
            int returnCode = 200;

            if(this.sleep){
                returnCode = 400;
                respText = gson.toJson(new StatusReply(false));
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
           }

            if ("POST".equals(exchange.getRequestMethod())) {
                GetChainRequest getChainRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    getChainRequest = gson.fromJson(isr, GetChainRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int chain_id = getChainRequest.getChainId();
                List<Block> blocks = new LinkedList<>();
                if (chain_id == 1) {
                    blocks = this.id_chain;
                } else if (chain_id == 2) {
                    blocks = this.vote_chain;
                } else {
                    respText = "wrong chain_id!\n";
                    returnCode = 404;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                int chain_length = blocks.size();
                GetChainReply getChainReply = new GetChainReply(chain_id, chain_length, blocks);
                respText = gson.toJson(getChainReply);
            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));

    }

    private void synchronize_block_chain(int chain_id) {
        LinkedList<Block> block_chain;
        if (chain_id == 1) {
            block_chain = id_chain;
        } else{
            block_chain = vote_chain;
        }
        for (int i = 0; i < ports.size(); i++) {
            if (i == this.nodeId) {
                continue;
            }
            String uri = HOST_URI + ports.get(i) + BROADCAST_URI;
            GetChainRequest request = new GetChainRequest(chain_id);
            try {
                GetChainReply reply = messageSender.post(uri, request, GetChainReply.class);
                if(reply.getChainLength()>block_chain.size()){
                    //need to update
                    block_chain.clear();
                    block_chain.addAll(reply.getBlocks());
                    System.out.println("Node "+ String.valueOf(nodeId)+" synchronized from "+String.valueOf(i));
                    System.out.flush();
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }





    }
    private void mineBlock() {
        this.node_skeleton.createContext("/mineblock", (exchange -> {
            FileOutputStream f = new FileOutputStream("node.log",true);
            System.setOut(new PrintStream(f));
            String respText = "";
            int returnCode = 200;

            if(this.sleep){
                returnCode = 400;
                respText = gson.toJson(new StatusReply(false));
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
           }
           
            if ("POST".equals(exchange.getRequestMethod())) {
                MineBlockRequest mineBlockRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    mineBlockRequest = gson.fromJson(isr, MineBlockRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                

                int chain_id = mineBlockRequest.getChainId();
                Map<String, String> data = mineBlockRequest.getData();

                if (chain_id == 1) {
                    synchronize_block_chain(1);
                    // id chain
                    Block block = new Block(id_chain.size(), data, System.currentTimeMillis(), 0,
                            id_chain.getLast().getHash(), "");
                    while (true) {
                        String hash = Block.computeHash(block);
                        if (hash.startsWith("00000")) {
                            block.setHash(hash);
                            break;
                        } else {
                            block.nonceIncrement();
                        }
                    }
//                    id_chain.add(block);
//                    if (id_chain.size() == 2 &&  id_chain.getFirst().getHash().equals( "dummy")) {
//                        id_chain.removeFirst();
//                    }
                    BlockReply reply = new BlockReply(chain_id, block);
                    respText = gson.toJson(reply);
                    System.out.println(respText);
                    System.out.flush();
                    returnCode = 200;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;

                } else if (chain_id == 2) {
                    synchronize_block_chain(2);
                    // vote chain
                    Block block = new Block(vote_chain.size(), data, System.currentTimeMillis(), 0,
                            vote_chain.getLast().getHash(), "");
                    String hash = Block.computeHash(block);
                    block.setHash(hash);
//                    vote_chain.add(block);
//                    first one
//                    if (vote_chain.size() == 2 &&  vote_chain.getFirst().getHash().equals( "dummy")) {
//                        vote_chain.removeFirst();
//                    }
                    BlockReply reply = new BlockReply(chain_id, block);
                    respText = gson.toJson(reply);
                    returnCode = 200;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                } else {
                    respText = "wrong chain_id!\n";
                    returnCode = 404;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                }

            } else {
                respText = "The REST method should be POST for <node>!\n";
                returnCode = 400;
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));

    }

    private void addBlock() {
        this.node_skeleton.createContext("/addblock", (exchange -> {
            String respText = "";
            int returnCode = 200;

            if(this.sleep){
                returnCode = 400;
                respText = gson.toJson(new StatusReply(false));
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
           }
            if ("POST".equals(exchange.getRequestMethod())) {
                AddBlockRequest addBlockRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    addBlockRequest = gson.fromJson(isr, AddBlockRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                
                int chain_id = addBlockRequest.getChainId();
                Block block = addBlockRequest.getBlock();
                LinkedList<Block> block_chain;

                System.out.println("Get Request "+block.toString());

                int vote = 1;
                Boolean valid = false;
                if (chain_id == 1) {
                    System.out.println(id_chain.getLast().getHash());
                    System.out.println(block.getPreviousHash());
                    if(block.getId() == id_chain.size() &&
                               block.getPreviousHash().equals( id_chain.getLast().getHash()) &&
                               block.getHash().startsWith("00000")){
                            valid = true;
                    }
                    block_chain = id_chain;
                } else if (chain_id == 2) {
                    if(block.getId() == vote_chain.size() && 
                        block.getPreviousHash().equals(vote_chain.getLast().getHash())){
                            valid = true;
                    }
                    block_chain = vote_chain;
                } else {
                    respText = "wrong chain_id!\n";
                    returnCode = 404;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                if(!valid){
                    returnCode = 409;
                    StatusReply reply = new StatusReply(false);
                    respText = gson.toJson(reply);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    System.out.println("invalid with local blockChain");
                    System.out.flush();
                    return;
                }
//                System.out.println("start precommit");
                // PRECOMMIT
                for (int i = 0; i < ports.size(); i++) {
                    if (i == this.nodeId) {
                        System.out.println("i == this.nodeId");
                        System.out.println(this.nodeId);
                        continue;
                    }
                    BroadcastRequest request = new BroadcastRequest(chain_id, "PRECOMMIT", block);
                    System.out.println("try broadcast " + String.valueOf(ports.get(i)));
                    String uri = HOST_URI + ports.get(i) + BROADCAST_URI;
                    
                    StatusReply reply;
                    try {
                        reply = messageSender.post(uri, request, StatusReply.class);
                        if (reply != null && reply.getSuccess())
                        {
                            vote++;
                        }
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                Boolean success = false;
                int majority = (int) Math.ceil(ports.size() * 2.0 / 3);
                System.out.println("majority "+String.valueOf(majority));
                System.out.println("vote result "+ String.valueOf(vote));
                if(vote >= majority){
                    //Commit
                    System.out.println("try commit");
                    block_chain.add(block);
                    success = true;
                    returnCode =200;
                    for (int i = 0; i < ports.size(); i++) {
                        if (i == this.nodeId) {
                            continue;
                        }
                        BroadcastRequest request = new BroadcastRequest(chain_id, "COMMIT", block);
                        String uri = HOST_URI + ports.get(i) + BROADCAST_URI;
                        StatusReply reply;
                        try {
                            reply = messageSender.post(uri, request, StatusReply.class);
                            if (reply != null && reply.getSuccess())
                            {
                                vote++;
                            }
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }else{
                    System.out.println("fail commit");
                    returnCode = 409;
                }
                StatusReply reply = new StatusReply(success);
                respText = gson.toJson(reply);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }else{
                respText = "The REST method should be POST for <node>!\n";
                returnCode = 400;
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
            }
        }));
    }

    private void broadcast(){
        this.node_skeleton.createContext("/broadcast", (exchange -> {
            String respText = "";
            int returnCode = 200;

            if(this.sleep){
                returnCode = 400;
                respText = gson.toJson(new StatusReply(false));
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
           }
            if ("POST".equals(exchange.getRequestMethod())) {
                BroadcastRequest broadcastRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    broadcastRequest = gson.fromJson(isr, BroadcastRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                // System.out.println("start broadcast");
                int chain_id = broadcastRequest.getChainId();
                String type = broadcastRequest.getRequestType();
                Block block = broadcastRequest.getBlock();
                Boolean vote = false;
                if(chain_id == 1){
                    if (type.equals("PRECOMMIT")){
                        if(block.getId() == id_chain.size() && 
                        block.getPreviousHash().equals(id_chain.getLast().getHash() )&&
                        block.getHash().startsWith("00000")){
                            vote = true;
                        }
                    }else if (type.equals("COMMIT")){
                        if(block.getId() == id_chain.size() && 
                        block.getPreviousHash().equals(id_chain.getLast().getHash()) &&
                        block.getHash().startsWith("00000")){
                            id_chain.add(block);
                            vote = true;
                        }

                    }else{
                        respText = "wrong request type!\n";
                        returnCode = 404;
                        this.generateResponseAndClose(exchange, respText, returnCode);
                        return;
                    }
                }else if (chain_id ==2){
                    if (type.equals("PRECOMMIT")){
                        if(block.getId() == vote_chain.size() &&
                                   block.getPreviousHash().equals(vote_chain.getLast().getHash())){
                            vote = true;
                        }
                    }else if (type.equals("COMMIT")){
                        if(block.getId() == vote_chain.size() &&
                                   block.getPreviousHash().equals(vote_chain.getLast().getHash())){
                            vote_chain.add(block);
                            vote = true;
                        }
                    }else{
                        respText = "wrong request type!\n";
                        returnCode = 404;
                        this.generateResponseAndClose(exchange, respText, returnCode);
                        return;
                    }
                    if(vote){
                        returnCode = 200;
                    }else{
                        returnCode = 409;
                    }

                }else{
                    respText = "wrong chain_id!\n";
                    returnCode = 404;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                StatusReply reply = new StatusReply(vote);
                respText = gson.toJson(reply);
                this.generateResponseAndClose(exchange, respText, returnCode);

            }else{
                respText = "The REST method should be POST for <node>!\n";
                returnCode = 400;
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
            }
        }));
    }

    /**
     * call this function when you want to write to response and close the connection.
     */
    private void generateResponseAndClose(HttpExchange exchange, String respText, int returnCode) throws IOException {
        exchange.sendResponseHeaders(returnCode, respText.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(respText.getBytes());
        output.flush();
        exchange.close();
    }

    public static void main(String[] args) throws Exception{
        if (args.length != 2) throw new Exception("Need 2 args: <index id> <port list> ");
        FileOutputStream f = new FileOutputStream("node.log",true);
        System.setOut(new PrintStream(f));
        Node node = new Node(args[0], args[1]);
//        node.start();
    }


}
