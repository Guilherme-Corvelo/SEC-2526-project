# SEC-2526-project
SEC 25/26 project

## Phase 1: Byzantine Consensus

This repository contains the implementation of DepChain, a permissioned blockchain system built iteratively.

**Phase 1 (Consensus Only)** focuses on the consensus layer with the Byzantine HotStuff algorithm.

### Completed Steps

1. **Step 1: HotStuff Algorithm Design**
   - Understanding and designing the HotStuff consensus algorithm
   - Leader-based multi-phase consensus with safety and liveness guarantees

2. **Step 2: Authenticated Perfect Links (APL)**
   - Reliable, authenticated point-to-point communication over UDP
   - Message authentication via HMAC-SHA256
   - Automatic retransmission on timeout

3. **Step 3: HotStuffNode (Crash-Tolerant)**
   - Basic implementation of HotStuff with f crash-faults tolerance (f < n/3)
   - Handles prepare, pre-commit, and commit phases
   - Maintains view history and performs view changes

4. **Step 4: Timeout Failure Detector**
   - Detects crash failures via timeout-based mechanism
   - Triggers view changes when leader is suspected dead
   - Integrates with HotStuffNode for resilience

5. **Step 5: Byzantine HotStuffNode**
   - Full Byzantine-Fault-Tolerant consensus (tolerating f = 1 Byzantine fault with n = 4)
   - **Cryptographic protections:**
     - RSA-SHA256 signatures on all votes
     - Signature verification in QC validation
     - Equivocation detection (preventing double-voting in same view)
     - View numbers included in signatures (replay prevention)
     - Proper 2f+1 Byzantine quorum enforcement (3 out of 4)
   - **10 comprehensive tests validating all 5 attack vectors:**
     - ✓ Forged vote rejection
     - ✓ Byzantine quorum enforcement  
     - ✓ Fake message rejection
     - ✓ Equivocation detection
     - ✓ Replay attack prevention

6. **Step 6: Client Library & Append-Only Blockchain Structure**
   - **ServiceClient**: Minimal fire-and-forget client for submitting strings
   - **Block**: Simple data container for consensus blocks
   - **AppendOnlyLog**: In-memory append-only log storing committed entries
   - **BlockchainService**: Service implementation maintaining the append-only log
   - **Phase 1 scope**: Consensus-only; client submits data, consensus handles ordering, service appends to log
   - Ready for Phase 2: service layer enhancement, commitment confirmation, client-side logging

### Test Results
- **22 tests passing** across all components
  - 7 APL network tests
  - 5 basic HotStuff tests
  - 10 Byzantine HotStuff tests

### Running Tests
```bash
mvn clean compile  # Build the project
mvn test          # Run all unit tests
```

### Architecture

```
src/main/java/depchain/
├── consensus/          # HotStuff consensus protocols
│   ├── Vote.java
│   ├── SignedVote.java
│   ├── QuorumCertificate.java
│   ├── Block.java
│   ├── HotStuffNode.java
│   ├── ByzantineHotStuffNode.java
│   ├── ConsensusListener.java
│   └── TimeoutFailureDetector.java
├── network/            # Authenticated Perfect Links (APL)
│   ├── APLListener.java
│   ├── APLMessage.java
│   └── AuthenticatedPerfectLinksImpl.java
├── service/            # Service layer (append-only log)
│   ├── AppendOnlyLog.java
│   └── BlockchainService.java
└── client/             # Client library
    └── ServiceClient.java
```

### Phase 2 Preview (Not Yet Implemented)
The following will be added in Phase 2:
- Service adapter layer for translating client requests into consensus blocks
- Commitment confirmation protocol (clients wait for committed blocks)
- Client-side append-only log with persistence
- Transaction semantics (proper request/reply)
