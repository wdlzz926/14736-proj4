package message;

public class Encrypted_vote {
    private int chain_id;
    private String user_name;
    private String voted_for;
    private String signature;

    public Encrypted_vote(String user_name, String voted_for, String signature){
        this.chain_id = 2;
        this.user_name = user_name;
        this.voted_for = voted_for;
        this.signature = signature;

    }

    public int getChain_id() {
        return chain_id;
    }

    public String getUserName(){
        return this.user_name;
    }

    public String getVotedFor(){
        return this.voted_for;
    }

    public String getSignature() {
        return this.signature;
        
    }


}