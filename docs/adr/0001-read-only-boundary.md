# ADR 0001: Keep MarketGuard read-only

Status: Accepted

MarketGuard uses only public market-data and OAuth token endpoints. Account identifiers, positions, balances, order placement/cancellation, transfers, and fund movement are outside the model and deployment configuration.

This boundary reduces financial harm, credential scope, regulatory ambiguity, and test/deployment risk. A future trading capability must be a different system and security review, not an extension hidden behind a flag.
