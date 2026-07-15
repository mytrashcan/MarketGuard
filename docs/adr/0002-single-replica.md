# ADR 0002: Support one collector replica per Toss client

Status: Accepted

Toss documents one valid access token per client; issuing a new token immediately invalidates the previous one. Multiple replicas sharing one credential can therefore invalidate each other.

MarketGuard supports one application replica per Toss client credential. Durable cooldown is still database-concurrent for correctness, but the token manager, WebSocket broker, and inbound rate limiter remain process-local. Horizontal scaling requires a different credential/token ownership design and distributed broker/limiter.
