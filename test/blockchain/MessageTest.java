/**
 *
 *      File Name -     MessageTest.java
 *      Created By -    14736 Spring 2020 TAs
 *      Brief -
 *
 *          Tests the different API calls that blockchain nodes must handle
 *
 *          For detailed information about the API calls, see -
 *                      API/BlockChainNode.md
 */


package test.blockchain;

import java.util.Map;
import java.util.TreeMap;

import test.util.TestFailed;

import message.*;

/**
 *      Basic blockchain message tests
 */
public class MessageTest extends NodeTest
{
    /** Test notice. */
    public static final String notice =
            "Testing blockchain message handling";

    /** Performs the tests.

     @throws TestFailed If any of the tests fail.
     */
    @Override
    protected void perform() throws TestFailed
    {
        System.out.println("perform");
        testGetChainRequest();
        testMineBlockRequest();
        testAddBlockRequest();
        testBroadcastRequest();
        testSleepRequest();
    }

    /**
     *      Tests the GetChain API call
     *
     *      @throws TestFailed
     */
    private void testGetChainRequest() throws TestFailed
    {
        System.out.println("GetChainRequest");
        for (int c = 0; c < 2; c++)
        {
            int chain_id = CHAIN_IDS[c];
//            System.out.println("testGetChainRequest");

            GetChainRequest request = new GetChainRequest(chain_id);
//            System.out.println("get testGetChainRequest result");
            for (int i = 0; i < nodes.size(); i++) {
                int port = nodes.get(i);
                String uri = HOST_URI + port + GET_CHAIN_URI;

                GetChainReply reply;
                try
                {
                    reply = client.post(uri, request, GetChainReply.class);

                    if (reply == null) throw new Exception();
                }
                catch (Exception ex)
                {
                    throw new TestFailed("GetBlockChain failed: " +
                            "No response or incorrect format.");
                }

                if (reply.getChainId() != chain_id)
                {
                    throw new TestFailed("GetBlockChain failed: " +
                            "Incorrect chain_id.");
                }
                if (reply.getChainLength() != reply.getBlocks().size())
                {
                    throw new TestFailed("GetBlockChain failed: " +
                            "Unmatched chain length.");
                }
            }
        }
    }


    /**
     *      Tests the MineBlock API call
     *
     *      @throws TestFailed
     */
    private void testMineBlockRequest() throws TestFailed
    {
        System.out.println("MineBlock");
        for (int c = 0; c < 2; c++)
        {
            int chain_id = CHAIN_IDS[c];
            Map<String, String> data = new TreeMap<>();

            MineBlockRequest request = new MineBlockRequest(chain_id, data);

            for (int i = 0; i < nodes.size(); i++) {
                int port = nodes.get(i);
                String uri = HOST_URI + port + MINE_BLOCK_URI;

                BlockReply reply;
                try
                {
                    reply = client.post(uri, request, BlockReply.class);
//                    System.out.println("reply: ");
//                    System.out.println(reply);
                    if (reply == null) throw new Exception();
                }
                catch (Exception ex)
                {
                    throw new TestFailed("MineBlock failed: " +
                            "No response or incorrect format.");
                }

                if (reply.getChainId() != chain_id)
                {
                    throw new TestFailed("MineBlock failed: " +
                            "Incorrect chain_id.");
                }
                if (reply.getBlock() == null)
                {
                    throw new TestFailed("MineBlock failed: " +
                            "Empty block.");
                }
            }
        }
    }

    /**
     *      Tests the AddBlock API call
     *
     *      @throws TestFailed
     */
    private void testAddBlockRequest() throws TestFailed
    {
        System.out.println("AddBlock");
        for (int c = 0; c < 2; c++)
        {
            int chain_id = CHAIN_IDS[c];
            Block block = new Block();

            AddBlockRequest request = new AddBlockRequest(chain_id, block);

            for (int i = 0; i < nodes.size(); i++) {
                int port = nodes.get(i);
                String uri = HOST_URI + port + ADD_BLOCK_URI;

                StatusReply reply;
                try
                {
                    reply = client.post(uri, request, StatusReply.class);
                    if (reply == null) throw new Exception();
                }
                catch (Exception ex)
                {
                    throw new TestFailed("AddBlock failed: " +
                            "No response or incorrect format.");
                }

                if (reply.getSuccess())
                {
                    throw new TestFailed("AddBlock failed: " +
                            "Expect failure, return success.");
                }
            }
        }
    }

    /**
     *      Tests the BroadcastBlock API call
     *
     *      @throws TestFailed
     */
    private void testBroadcastRequest() throws TestFailed
    {
        System.out.println("Broadcast");
        for (int c = 0; c < 2; c++)
        {
            int chain_id = CHAIN_IDS[c];
            String request_type = BROADCAST_TYPES[0];
            Block block = new Block();

            BroadcastRequest request = new BroadcastRequest(chain_id,
                    request_type, block);

            for (int i = 0; i < nodes.size(); i++) {
                int port = nodes.get(i);
                String uri = HOST_URI + port + BROADCAST_URI;

                StatusReply reply;
                try
                {
                    reply = client.post(uri, request, StatusReply.class);
                    if (reply == null) throw new Exception();
                }
                catch (Exception ex)
                {
                    throw new TestFailed("BroadcastBlock failed: " +
                            "No response or incorrect format.");
                }
            }
        }
    }


    /**
     *      Tests the Sleep API call
     *
     *      @throws TestFailed
     */
    private void testSleepRequest() throws TestFailed
    {
        System.out.println("Sleep");
        SleepRequest request = new SleepRequest(SLEEP_TIMEOUT);
        int chain_id = 1;

        for (int i = 0; i < nodes.size(); i++)
        {
            Block block = mineBlock(i, chain_id, new TreeMap<>());

            int port = nodes.get(i);
            String uri = HOST_URI + port + SLEEP_URI;

            StatusReply reply;
            try
            {
//                System.out.println("reply: ");
                reply = client.post(uri, request, StatusReply.class);
//                System.out.println(reply);
            }
            catch (Exception ex)
            {
                throw new TestFailed("Sleep failed: " +
                        "No response or incorrect format.");
            }

            if (!reply.getSuccess())
            {
                throw new TestFailed("Sleep failed: Expect success.");
            }

            boolean add_success = false;
            try
            {
                add_success = addBlock(i, chain_id, block);
            }
            catch (TestFailed tf)
            {
                // Ignore exception because request failure is expected
            }

            if (add_success)
            {
                throw new TestFailed("Sleep failed: " +
                        "Expect failure for AddBlock requests.");
            }

            boolean broadcast_success = false;
            try
            {
                broadcast_success = broadcastBlock(i, chain_id, block,
                        "PRECOMMIT");
            }
            catch (TestFailed tf)
            {
                // Ignore exception because request failure is expected
            }

            if (broadcast_success)
            {
                throw new TestFailed("Sleep failed: " +
                        "Expect failure for BroadcastBlock requests.");
            }
        }
    }
}
