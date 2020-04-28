package client;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.codec.binary.Hex;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import lib.MessageSender;
import message.*;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;

/**
 * client class:  all types of messages that a client node shall handle.
 */
public class Client {
    protected static final String HOST_URI = "http://127.0.0.1:";
    protected static final String GET_CHAIN_URI = "/getchain";
    protected static final String MINE_BLOCK_URI = "/mineblock";
    protected static final String ADD_BLOCK_URI = "/addblock";
    protected static final String CASTVOTE_URI = "/castvote";

    private HttpServer client_skeleton;
    protected Gson gson;
    private String clientId;
    private String serverId;
    private int blockchainId;
    private PrivateKey pvt;
    private PublicKey pub;
    private SecretKey secretKey;
    private MessageSender messageSender = new MessageSender(10);

    /**
     * constructor
     * @param client: client port
     * @param server: server port
     * @param blockchain: blockchain id
     */
    Client(String client, String server, String blockchain) {
        clientId = client;
        serverId = server;
        blockchainId = Integer.parseInt(blockchain);
        this.gson = new Gson();
        registration_rsa_key();
        try {
            this.client_skeleton = HttpServer.create(new InetSocketAddress(Integer.parseInt(client)), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.client_skeleton.setExecutor(null);

        registration_aes_key();
        this.add_client_api();
        this.client_skeleton.start();
    }

    /**
     * create session key.
     */
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

    /**
     * Register public key pair with key blockchain.
     */
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
            e.printStackTrace(System.out);
        }
    }

    private void add_client_api() {
        this.startVote();
    }

    /**
     * sign digital signature.
     * @param data the data need to be sign.
     * @return signed data.
     * @throws Exception
     */
    public byte[] sign(String data) throws Exception{
        Signature rsa = Signature.getInstance("SHA1withRSA");
        rsa.initSign(pvt);
        rsa.update(data.getBytes());
        return rsa.sign();
    }

    /**
     * using session key to encrypt data.
     * @param plain plain data
     * @return encrypted data.
     */
    private String encrypt_aes(String plain) {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
        try {
            assert cipher != null;
            byte[] iv = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
            IvParameterSpec ivspec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,ivspec);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace(System.out);
        }
        byte[] cipherText = new byte[0];
        try {
            cipherText = cipher.doFinal(plain.getBytes());
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        String send = Base64.getEncoder().encodeToString(cipherText);
        return send;
    }

    /**
     * start vote API: Cast a vote to the requested candidate after the appropriate encryptions in place.
     */
    private void startVote() {
        this.client_skeleton.createContext("/startvote", (exchange -> {
            String respText = "";
            int returnCode = 200;
            FileOutputStream f = new FileOutputStream("client.log",true);
            System.setOut(new PrintStream(f));
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
                String candidate = startVoteRequest.getVoteFor();
                String encrypted_vote = "\"user_name\":" + clientId + ",\n \"voted_for\": " + candidate;
                byte[] sign_vote_byte = new byte[0];
                try {
                    sign_vote_byte = sign(encrypted_vote);
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
                String sign_vote = Base64.getEncoder().encodeToString(sign_vote_byte);
                Encrypted_vote vote_content = new Encrypted_vote(clientId, candidate, sign_vote);
                String vote_content_string = gson.toJson(vote_content);
                String encrypt_vote_content = encrypt_aes(vote_content_string);
                String encrypt_session_key = null;
                try {
                    encrypt_session_key = encrypt_public(secretKey.getEncoded());
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
                CastVoteRequest castVoteRequest = new CastVoteRequest(encrypt_vote_content, encrypt_session_key);
                String uri = HOST_URI + serverId + CASTVOTE_URI;
                StatusReply statusReply = null;
                try {
                    statusReply = messageSender.post(uri,gson.toJson(castVoteRequest), StatusReply.class);
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
                respText = gson.toJson(statusReply);
            } else {
                respText = "The REST method should be POST for <service api>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    /**
     * using server public key to encrypt data.
     * @param encoded data need to be encrypt
     * @return encrypt data.
     * @throws Exception
     */
    private String encrypt_public(byte[] encoded) throws Exception {
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
        cipher.update(encoded);
        byte[] cipherText = new byte[0];
        try {
            cipherText = cipher.doFinal();
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace(System.out);
        }
        String send = Base64.getEncoder().encodeToString(cipherText);
        return send;
    }

    /**
     * get server public key.
     * @return server public key
     * @throws Exception
     */
    private PublicKey getPulicKey() throws Exception {
        String uri = HOST_URI + blockchainId + GET_CHAIN_URI;
        GetChainRequest chain_requst = new GetChainRequest(1);
        GetChainReply chain_reply = messageSender.post(uri, gson.toJson(chain_requst), GetChainReply.class);
        List<Block> blocks = chain_reply.getBlocks();
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
