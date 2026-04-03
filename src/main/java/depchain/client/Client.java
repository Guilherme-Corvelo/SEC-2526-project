package depchain.client;

import depchain.Debug;
import depchain.API.DepchainAPI;
import depchain.blockchain.ExecutionResult;
import depchain.blockchain.Transaction;
import depchain.consensus.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

public class Client{
    private DepchainAPI depchain;
    
    public Client(int clientId,
                    int port, Map<Integer, InetSocketAddress> serviceAddress,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys, int f) throws IOException{

        this.depchain = new DepchainAPI(clientId, port, serviceAddress, privateKey, publicKeys,f);
    }

    public Message send(Transaction transaction){
        Debug.debug("Client Sent transaction");
        Message response = depchain.append(transaction);
        Debug.debug("Client received response");
        printExecutionResult(response, transaction);
        return response;
    }

    private void printExecutionResult(Message response, Transaction transaction) {
        if (response == null || response.getExecutionResults() == null || response.getExecutionResults().isEmpty()) {
            System.out.println("No execution result was returned by the service.");
            return;
        }

        int txIndex = -1;
        if (response.getNode() != null && response.getNode().getProposedTransactions() != null) {
            txIndex = response.getNode().getProposedTransactions().indexOf(transaction);
        }

        ExecutionResult result;
        if (txIndex >= 0 && txIndex < response.getExecutionResults().size()) {
            result = response.getExecutionResults().get(txIndex);
        } else {
            result = response.getExecutionResults().get(0);
        }

        System.out.println("Execution result:");
        System.out.println("  success: " + result.success);
        System.out.println("  gasUsed: " + result.gasUsed);
        System.out.println("  returnValue: " + result.returnValue);
    }
}
