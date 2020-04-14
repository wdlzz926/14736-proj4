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

    protected HttpResponse<String> getResponse(String method, int port, Object requestObj)
            throws IOException, InterruptedException {

        HttpResponse<String> response;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + method))
                                      .setHeader("Content-Type", "application/json")
                                      .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestObj))).build();

        response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

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
    }

    private void getBlockChain(){
        this.node_skeleton.createContext("/getchain", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                GetChainRequest getChainRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    getChainRequest = gson.fromJson(isr, GetChainRequest.class);
                } catch (Exception e) {
                    System.out.print(exchange.getRequestBody());
                    respText = "Error during parse JSON object!\n";
                    returnCode = 404;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int chain_id = getChainRequest.getChainId();
                List<Block> blocks = this.id_chain;
                int chain_length = blocks.size();
                GetChainReply getChainReply = new GetChainReply(chain_id, chain_length, blocks);
                respText = gson.toJson(getChainReply);
            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 404;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));

    }

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
