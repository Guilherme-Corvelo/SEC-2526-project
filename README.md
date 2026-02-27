# SEC-2526-project
SEC 25/26 project

This repository contains the implementation of DepChain, a permissioned
blockchain system built iteratively. The first stage focuses on the
consensus layer, starting with the Basic HotStuff algorithm.  

The initial work implements the **Authenticated Perfect Links** abstraction
which provides reliable, authenticated point-to-point communication over an
unreliable UDP network.  

The code is organised as a Maven project; run `mvn test` to compile and
execute the unit tests that accompany the abstractions.
