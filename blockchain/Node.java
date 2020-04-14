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
import message.*;

public class Node {

    private HttpServer node_skeleton;
    protected Gson gson;
    private List<Integer> ports;
    private int nodeId;
    private LinkedList<Block> id_chain = new LinkedList<Block>();
    private LinkedList<Block> vote_chain = new LinkedList<Block>();

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
        this.mineBlock();
    }

    private void getBlockChain(){

    }

    private void mineBlock(){
        this.node_skeleton.createContext("/getchain", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                MineBlockRequest mineBlockRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    mineBlockRequest = gson.fromJson(isr, MineBlockRequest.class);
                } catch (Exception e) {
                    System.out.print(exchange.getRequestBody());
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                //TODO: synchronize before mine
                


                int chain_id = mineBlockRequest.getChainId();
                Map<String, String> data = mineBlockRequest.getData();
                if(chain_id ==1){
                    //id chain
                    Block block = new Block(id_chain.size(), data, 
                                        System.currentTimeMillis(), 0,id_chain.getLast().getHash(),"");
                    while(true){
                        String hash = Block.computeHash(block);
                        if(hash.startsWith("00000")){
                            block.setHash(hash);
                            break;
                        }else{
                            block.nonceIncrement();
                        }
                    }
                    id_chain.add(block);
                    BlockReply reply = new BlockReply(chain_id, block);
                    respText = gson.toJson(reply);
                    returnCode = 200;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;

                }else{
                    //vote chain
                    Block block = new Block(vote_chain.size(), data, 
                                        System.currentTimeMillis(), 0,vote_chain.getLast().getHash(),"");
                    String hash = Block.computeHash(block);
                    block.setHash(hash);
                    vote_chain.add(block);
                    BlockReply reply = new BlockReply(chain_id, block);
                    respText = gson.toJson(reply);
                    returnCode = 200;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                

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
