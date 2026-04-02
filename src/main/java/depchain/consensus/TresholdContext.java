package depchain.consensus;

import java.util.Arrays;

import threshsig.Dealer;
import threshsig.GroupKey;
import threshsig.KeyShare;
import threshsig.ThresholdSigException;


class ThresholdContext {
    private final GroupKey groupKey;
    private final KeyShare[] shares;
    private static final int THRESHOLD_KEY_BITS = 512;
    
    public ThresholdContext(int k, int n) {
        try {
            Dealer dealer = new Dealer(THRESHOLD_KEY_BITS);
            dealer.generateKeys(k, n);
            this.groupKey = dealer.getGroupKey();
            this.shares = Arrays.copyOf(dealer.getShares(), dealer.getShares().length);

        } catch (ThresholdSigException e) {
            throw new IllegalStateException("Failed to initialize threshold signature keys", e); 
        }
    }

    public KeyShare getShare(int id){
        return shares[id];
    }

    public GroupKey getGroupKey(){
        return groupKey;
    }
}
