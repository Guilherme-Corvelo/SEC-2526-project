# DepChain (SEC 25/26 Project)

DepChain is a Java/Maven project that combines:
- a Byzantine fault-tolerant consensus layer that uses the algotithm HotStuff,
- a networking layer with authenticated point-to-point messaging,
- a blockchain execution/storage layer,
- a client CLI used to submit transactions and queries.

## Project structure

```text
src/main/java/
├── depchain/
│   ├── API/           # Request/API-facing types
│   ├── blockchain/    # Blocks, transactions, execution, storage
│   ├── client/        # Client runtime + interactive CLI
│   ├── consensus/     # Node logic, message/QC types, server bootstrap
│   ├── crypto/        # Key generation/loading utilities
│   └── network/       # APL transport abstractions and messages
└── threshsig/         # Threshold signature primitives/helpers

src/test/java/depchain/
├── blockchain/        # Block, tx, processor, integration tests
├── consensus/         # Byzantine/HotStuff node tests
├── contract/          # ISTCoin-related tests
├── integration/       # End-to-end multi-component integration tests
├── network/           # APL/network serialization tests
```

Other important directories:
- `privateKeys/`, `publicKeys/`: node/client RSA keys.
- `thresholdKeys/`: generated threshold signature key material.
- `genesis.json`, `ISTCoin.sol`: chain/contract artifacts used by the project.
- `nonces/`: client nonces for client persistence.

## Build and run tests

### 1) Compile
```bash
mvn clean compile
```

### 2) Generate keys
If the keys are yet to be created:
```bash
mvn exec:java -Dexec.mainClass="depchain.crypto.KeyVault"
```

### 3) Run all tests
```bash
mvn test
```

### 4) Run a single test class (example)
```bash
mvn test -Dtest=depchain.blockchain.BlockchainIntegrationTest 
```

## Demo: run local servers + CLI client

### 0) Generate keys
If the keys are yet to be created:
```bash
mvn exec:java -Dexec.mainClass="depchain.crypto.KeyVault"
```

### 1) Start the server process
In terminal A:
```bash
mvn exec:java -Dexec.mainClass="depchain.consensus.ServerMain"
```

`ServerMain` boots the 4 local server nodes on ports `20003..20006`.

### 2) Start one client CLI
In terminal B (client 0 example):
```bash
mvn exec:java -Dexec.mainClass="depchain.client.ClientCLI" -Dexec.args="0 20000"
```

You can also start other clients by changing args:
- client 1: `-Dexec.args="1 20001"`
- client 2: `-Dexec.args="2 20002"`

### 3) Example for CLI commands
CLI prompt:
```text
help
ist total-supply
ist balance aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
depcoin balance aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

To exit:
```text
exit
```
