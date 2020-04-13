package blockchain;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;

public class Node {

    private class block{
        int id;
        Map<String, String> map;
        Timestamp timestamp;
        int nonce;
        String prehash;
        String hash;

//        public block(int id, Map<String, String> map, Timestamp timestamp, int nonce,
//                     String prehash, String hash) {
    public block(int id, String hash) {
            this.id = id;
//            this.map = map;
            this.timestamp = new Timestamp(System.currentTimeMillis());
//            this.nonce = nonce;
            this.prehash = hash;
//            this.hash = hash;
        }

        public void setHash(String string) throws NoSuchAlgorithmException {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(string.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hash);

            // Convert message digest into hex value
            StringBuilder hexString = new StringBuilder(number.toString(16));

            // Pad with leading zeros
            while (hexString.length() < 32)
            {
                hexString.insert(0, '0');
            }

            this.hash = hexString.toString();
        }
    }
    Node (String node_id, String port_list) {
        int nodeid = Integer.parseInt(node_id);
        List<String> portstr = Arrays.asList(port_list.split(","));
        List<Integer> ports = new ArrayList<>();
        for (String port : portstr) {
            ports.add(Integer.parseInt(port));
        }

    }
    public static void main(String[] args) throws Exception{
        if (args.length != 2) throw new Exception("Need 2 args: <index id> <port list> ");
        FileOutputStream f = new FileOutputStream("node.log",true);
        System.setOut(new PrintStream(f));
        Node node = new Node(args[0], args[1]);
//        node.start();
    }


}
