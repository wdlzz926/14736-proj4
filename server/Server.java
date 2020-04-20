package server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.io.*;
import lib.MessageSender;
import message.*;

public class Server {
    protected static final String HOST_URI = "http://127.0.0.1:";
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";
    protected static final String BROADCAST_URI = "/broadcast";

    private Gson gson = new Gson();
    private HttpServer server_skeleton;
    private MessageSender messageSender = new MessageSender(1);
    private int blockchain_port;
    private List<String> candidates = new LinkedList<String>();

    Server(int server_port, int blockchain_port){
        try {
            this.server_skeleton = HttpServer.create(new InetSocketAddress(server_port), 0);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.blockchain_port = blockchain_port;
        this.server_skeleton.setExecutor(null);
        this.add_api();
        this.server_skeleton.start();

    }

    private void add_api(){
        this.becomeCandidate();
        this.getCandidate();
        this.castVote();
        this.countVote();
    }

    private void becomeCandidate(){
        this.server_skeleton.createContext("/becomecandidate", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                BecomeCandidateRequest bcr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    bcr = gson.fromJson(isr, BecomeCandidateRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                //Get current id chain
                

            }else{
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    private void getCandidate(){
        this.server_skeleton.createContext("/getcandidates", (exchange -> {

        }));
    }

    private void castVote(){
        this.server_skeleton.createContext("/castvote", (exchange -> {

        }));
    }

    private void countVote(){
        this.server_skeleton.createContext("/countvotes", (exchange -> {

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
       if (args.length != 2) throw new Exception("Need 2 args: <server port> <blockchain port> ");
       FileOutputStream f = new FileOutputStream("voteserver.log",true);
       System.setOut(new PrintStream(f));
       Server server = new Server(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
   }
}
