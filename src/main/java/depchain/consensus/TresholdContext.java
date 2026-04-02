package depchain.consensus;

import java.util.Arrays;

import depchain.crypto.KeyVault;
import threshsig.GroupKey;
import threshsig.KeyShare;


class ThresholdContext {
    private final GroupKey groupKey;
    private final KeyShare[] shares;
    private static final int THRESHOLD_KEY_BITS = 512;
    
    public ThresholdContext(int k, int n) {
        try {
            if (!KeyVault.thresholdKeysExist(k, n)) {
                KeyVault.generateAndSaveThresholdKeys(k, n, THRESHOLD_KEY_BITS);
            }

            this.groupKey = KeyVault.loadGroupKey(k, n);
            this.shares = Arrays.copyOf(KeyVault.loadThresholdShares(k, n, this.groupKey), n);

        } catch (Exception e) {
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
