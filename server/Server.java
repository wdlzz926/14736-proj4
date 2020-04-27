package server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

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
    private MessageSender messageSender = new MessageSender(10);
    private int blockchain_port;
    private List<String> candidates = new LinkedList<String>();
    private Map<String,Integer> vote_count = new HashMap<String,Integer>();
    private Set<String> voted_client = new HashSet<String>();
    private PrivateKey pvt;
    private PublicKey pub;
    private String user_name;

    Server(int server_port, int blockchain_port) {
        try {
            this.server_skeleton = HttpServer.create(new InetSocketAddress(server_port), 0);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.user_name = String.valueOf(server_port);
        this.blockchain_port = blockchain_port;
        this.server_skeleton.setExecutor(null);

        registration_key();
        this.add_api();
        this.server_skeleton.start();

    }

    // Register public key pair with key blockchain
    private void registration_key() {
        System.out.println("Start registration");
        System.out.flush();
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            pub = kp.getPublic();
            pvt = kp.getPrivate();
            String pub_str = Base64.getEncoder().encodeToString(pub.getEncoded());
            Map<String, String> data = new HashMap<String, String>();
            data.put("user_name", this.user_name);
            data.put("public_key", pub_str);
            MineBlockRequest request = new MineBlockRequest(1, data);
            String uri = HOST_URI + blockchain_port + MINE_BLOCK_URI;
            System.out.println(gson.toJson(request));
            System.out.println(uri);
            BlockReply reply = messageSender.post(uri, gson.toJson(request), BlockReply.class);
            Block block = reply.getBlock();
            System.out.println("Server info Block: "+block.toString());
            System.out.flush();
            AddBlockRequest add_request = new AddBlockRequest(1, block);
            uri = HOST_URI + String.valueOf(blockchain_port) + ADD_BLOCK_URI;
            Boolean status = false;
            while (!status) {
                StatusReply add_reply = messageSender.post(uri, gson.toJson(add_request), StatusReply.class);
                status = add_reply.getSuccess();
            }

        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

    }

    private void add_api() {
        this.becomeCandidate();
        this.getCandidate();
        this.castVote();
        this.countVote();
    }

    private void becomeCandidate() {
        this.server_skeleton.createContext("/becomecandidate", (exchange -> {
            FileOutputStream f = new FileOutputStream("voteserver.log", true);
            System.setOut(new PrintStream(f));
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

                String candidate = bcr.getCandidateName();
                // check if already candidate
                if (candidates.contains(candidate)) {
                    returnCode = 409;
                    StatusReply reply = new StatusReply(false, "NodeAlreadyCandidate");
                    respText = gson.toJson(reply);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                // Get current id chain
                GetChainRequest chain_requst = new GetChainRequest(1);
                String uri = HOST_URI + String.valueOf(blockchain_port) + GET_CHAIN_URI;
                GetChainReply chain_reply;
                try {
                    chain_reply = messageSender.post(uri, gson.toJson(chain_requst), GetChainReply.class);
                    List<Block> blocks = chain_reply.getBlocks();
                    Boolean found = false;
                    for (Block b : blocks) {
                        if (candidate.equals(b.getData().get("user_name"))) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        candidates.add(candidate);
                        vote_count.put(candidate,0);
                        returnCode = 200;
                        StatusReply reply = new StatusReply(true);
                        respText = gson.toJson(reply);

                    } else {
                        returnCode = 422;
                        StatusReply reply = new StatusReply(false, "CandidatePublicKeyUnknown");
                        respText = gson.toJson(reply);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    private void getCandidate() {
        this.server_skeleton.createContext("/getcandidates", (exchange -> {
            FileOutputStream f = new FileOutputStream("voteserver.log", true);
            System.setOut(new PrintStream(f));
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                GetCandidatesReply reply = new GetCandidatesReply(candidates);
                respText = gson.toJson(reply);
                returnCode = 200;

            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    private void castVote() {
        this.server_skeleton.createContext("/castvote", (exchange -> {
            FileOutputStream f = new FileOutputStream("voteserver.log", true);
            System.setOut(new PrintStream(f));
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                CastVoteRequest cvr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    cvr = gson.fromJson(isr, CastVoteRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                String contents = cvr.getEncryptedVotes();
                String session_key = cvr.getEncryptedSessionKey();

                try {
                    Cipher cipher = Cipher.getInstance("RSA");
                    cipher.init(Cipher.DECRYPT_MODE, pvt);
                    byte[] tmp = Base64.getDecoder().decode(session_key);
                    byte[] key_bytes = cipher.doFinal(tmp);
                    SecretKeySpec key = new SecretKeySpec(key_bytes,"AES");
                    cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE,key);
                    String decrypt_contents = new String(cipher.doFinal(Base64.getDecoder().decode(contents)));
                    Encrypted_vote vote = gson.fromJson(decrypt_contents, Encrypted_vote.class);
                    
                    //check signature
                    PublicKey client_public = getPulicKey(vote.getUserName());
                    Signature sign = Signature.getInstance("SHA1withRSA");
                    sign.initVerify(client_public);
                    byte[] signature = Base64.getDecoder().decode(vote.getSignature());
                    Boolean verify = sign.verify(signature);
                    
                    if(!verify){
                        //wrong signature
                        throw new Exception();
                    }

                    String user_name = vote.getUserName();
                    if(voted_client.contains(user_name)){
                        returnCode = 409;
                        StatusReply reply = new StatusReply(false, "DuplicateVote");
                        respText = gson.toJson(reply);
                        this.generateResponseAndClose(exchange, respText, returnCode);
                        return;
                    }
                    
                    String vote_candidate = vote.getVotedFor();
                    if(candidates.contains(vote_candidate)){
                        int cur_vote = vote_count.get(vote_candidate);
                        vote_count.put(vote_candidate, cur_vote+1);
                        returnCode = 200;
                        StatusReply reply = new StatusReply(true);
                        respText = gson.toJson(reply);
                    }else{
                        returnCode = 422;
                        StatusReply reply = new StatusReply(false, "InvalidCandidate");
                        respText = gson.toJson(reply);
                    }




                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    returnCode = 422;
                    StatusReply reply = new StatusReply(false, "WrongDuringDecryption");
                    respText = gson.toJson(reply);
                    e.printStackTrace();
                }





            }else{
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    private PublicKey getPulicKey(String user_name) throws Exception {
        String uri = HOST_URI + blockchain_port + GET_CHAIN_URI;
        GetChainRequest chain_requst = new GetChainRequest(1);
        GetChainReply chain_reply = messageSender.post(uri, gson.toJson(chain_requst), GetChainReply.class);
        List<Block> blocks = chain_reply.getBlocks();
//        Boolean found = false;
        for (Block b : blocks) {
            if (user_name.equals(b.getData().get("user_name"))) {
                String publickey = b.getData().get("public_key");
                byte[] publicBytes = Base64.getDecoder().decode(publickey);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey pubKey = keyFactory.generatePublic(keySpec);
                return pubKey;
            }
        }
        return null;
    }

    private void countVote(){
        this.server_skeleton.createContext("/countvotes", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                CountVotesRequest cvr;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    cvr = gson.fromJson(isr, CountVotesRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }

                String candidate_id = cvr.getCountVotesFor();
                if(vote_count.containsKey(candidate_id)){
                    int count = vote_count.get(candidate_id);
                    CountVotesReply reply = new CountVotesReply(true, count);
                    returnCode = 200;
                    respText = gson.toJson(reply);
                }else{
                    returnCode = 422;
                    StatusReply reply = new StatusReply(false, "InvalidCandidate");
                    respText = gson.toJson(reply);
                }
                
            }else{
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
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
