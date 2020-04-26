package client;
import java.io.*;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.util.*;

import blockchain.Node;
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

import javax.crypto.*;

public class Client {
    protected static final String HOST_URI = "http://127.0.0.1:";
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";
    protected static final String BROADCAST_URI = "/broadcast";

    private HttpServer client_skeleton;
    protected Gson gson;
    private String clientId;
    private String serverId;
    private int blockchainId;
    private PrivateKey pvt;
    private PublicKey pub;
    private SecretKey secretKey;
    private String user_name;
    private MessageSender messageSender = new MessageSender(1);

    Client(String client, String server, String blockchain) {
//        clientId = Integer.parseInt(client);
        clientId = client;
        serverId = server;
        blockchainId = Integer.parseInt(blockchain);
        this.gson = new Gson();
        try {
            this.client_skeleton = HttpServer.create(new InetSocketAddress(Integer.parseInt(client)), 0);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        this.client_skeleton.setExecutor(null);
        registration_rsa_key();
        registration_aes_key();
        this.add_client_api();
//        this.id_chain.add(new Block());
//        this.vote_chain.add(new Block());
        this.client_skeleton.start();
    }

    private void registration_aes_key() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            SecureRandom secureRandom = new SecureRandom();
            int keyBitSize = 128;
            keyGenerator.init(keyBitSize, secureRandom);
            secretKey = keyGenerator.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Register public key pair with key blockchain
    private void registration_rsa_key() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            pub = kp.getPublic();
            pvt = kp.getPrivate();
            String pub_str = Base64.getEncoder().encodeToString(pub.getEncoded());
            Map<String,String> data = new HashMap<String,String>();
            data.put("user_name",this.clientId);
            data.put("public_key", pub_str);
            MineBlockRequest request = new MineBlockRequest(1, data);
            String uri = HOST_URI + blockchainId + MINE_BLOCK_URI;
            BlockReply reply = messageSender.post(uri, gson.toJson(request), BlockReply.class);
            Block block = reply.getBlock();
            AddBlockRequest add_request = new AddBlockRequest(1, block);
            uri = HOST_URI + blockchainId + ADD_BLOCK_URI;
            Boolean status = false;
            while(!status){
                StatusReply add_reply = messageSender.post(uri, gson.toJson(add_request), StatusReply.class);
                status = add_reply.getSuccess();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void add_client_api() {
        this.startVote();
    }

    public byte[] sign(String data) throws Exception{
        Signature rsa = Signature.getInstance("SHA1withRSA");
        rsa.initSign(pvt);
        rsa.update(data.getBytes());
        return rsa.sign();
    }
    private String encrypt_aes(String plain) {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
        try {
            assert cipher != null;
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        byte[] input = plain.getBytes();
        cipher.update(input);
        byte[] cipherText = new byte[0];
        try {
            cipherText = cipher.doFinal();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        String send = new String(cipherText, StandardCharsets.UTF_8);
        return send;
    }

    private void startVote() {
        this.client_skeleton.createContext("/startvote", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                StartVoteRequest startVoteRequest = null;
                try {
                    Gson gson = new Gson();
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    startVoteRequest = gson.fromJson(isr, StartVoteRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                int chain_id = startVoteRequest.getChainId();
                String candidate = startVoteRequest.getVoteFor();

                String encrypted_vote = "\"user_name\":" + clientId + ",\n \"voted_for\": " + candidate;
                byte[] sign_vote_byte = new byte[0];
                try {
                    sign_vote_byte = sign(encrypted_vote);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String sign_vote = new String(sign_vote_byte, StandardCharsets.UTF_8);
                Encrypted_vote vote_content = new Encrypted_vote(clientId, candidate, sign_vote);
                String vote_content_string = gson.toJson(vote_content);
                String encrypt_vote_content = encrypt_aes(vote_content_string);
                String encrypt_session_key = null;
                try {
                    encrypt_session_key = encrypt_public(secretKey.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                CastVoteRequest castVoteRequest = new CastVoteRequest(encrypt_vote_content, encrypt_session_key);
                String uri = HOST_URI + blockchainId + HOST_URI;
                StatusReply statusReply = null;
                try {
                    statusReply = messageSender.post(uri,gson.toJson(castVoteRequest), StatusReply.class);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                respText = gson.toJson(statusReply);
            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    private String encrypt_public(String plain) throws Exception {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("RSA");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
        try {
            assert cipher != null;
            PublicKey publicKey = getPulicKey();
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        byte[] input = plain.getBytes();
        cipher.update(input);
        byte[] cipherText = new byte[0];
        try {
            cipherText = cipher.doFinal();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        String send = new String(cipherText, StandardCharsets.UTF_8);
        return send;
    }

    private PublicKey getPulicKey() throws Exception {
        String uri = HOST_URI + blockchainId + GET_CHAIN_URI;
        GetChainRequest chain_requst = new GetChainRequest(1);
        GetChainReply chain_reply = messageSender.post(uri, gson.toJson(chain_requst), GetChainReply.class);
        List<Block> blocks = chain_reply.getBlocks();
//        Boolean found = false;
        for (Block b : blocks) {
            if (serverId.equals(b.getData().get("user_name"))) {
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
        if (args.length != 3) throw new Exception("Need 3 args: <index id> <port list> ");
        FileOutputStream f = new FileOutputStream("client.log",true);
        System.setOut(new PrintStream(f));
        Client client = new Client(args[0], args[1], args[2]);
    }

}
