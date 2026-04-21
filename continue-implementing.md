This issue crosses the deployment/plugin boundary. `craftalism-deployment` owned the Paper runtime
  wiring and I verified/fixed that side already.

  What I confirmed from the live Paper deployment:
  - The running `craftalism-minecraft` container was not receiving any market-specific runtime config.
  - The effective generated plugin config in `/data/plugins/CraftalismMarket/config.yml` was still on jar
  defaults:
    - `market-api.base-url: "http://127.0.0.1:8080"`
    - `market-api.snapshot-path: "/market/snapshot"`
    - `market-api.auth-token: ""`
  - From inside the Paper container:
    - `http://127.0.0.1:8080/market/snapshot` = connection refused
    - `http://craftalism-api:8080/market/snapshot` = reachable but `401 Unauthorized`
  - Symptom matches this exactly: `/market` shows “The market is unavailable right now. No cached data is
  available yet” because initial snapshot fetch fails and there is no cache yet.

  What I changed in `craftalism-deployment`:
  - Added explicit seeding of `/data/plugins/CraftalismMarket/config.yml` before Paper starts.
  - Deployment now writes market config from env instead of relying on market jar defaults.
  - Seeded values include:
    - `MARKET_API_BASE_URL` defaulting to `http://craftalism-api:8080`
    - `MARKET_API_SNAPSHOT_PATH=/market/snapshot`
    - `MARKET_API_QUOTE_PATH=/market/quote`
    - `MARKET_API_EXECUTE_PATH=/market/execute`
    - optional `MARKET_API_AUTH_TOKEN`
  - So deployment now handles base URL/path wiring correctly.

  The remaining problem is plugin auth design in `craftalism-market`.

  Relevant comparison with `craftalism-economy`:
  - Economy solves auth inside the plugin, not in deployment.
  - Economy reads:
    - `CRAFTALISM_API_URL`
    - `AUTH_ISSUER_URI`
    - `AUTH_TOKEN_PATH`
    - `MINECRAFT_CLIENT_ID`
    - `MINECRAFT_CLIENT_SECRET` or `CRAFTALISM_API_KEY`
  - Economy then mints and caches OAuth2 client-credentials bearer tokens internally.
  - Market does not currently do that.
  - Current market plugin only reads:
    - `market-api.base-url`
    - `market-api.snapshot-path`
    - `market-api.quote-path`
    - `market-api.execute-path`
    - `market-api.auth-token`

  Code references you should inspect:
  - `craftalism-market/src/main/java/io/github/henriquemichelini/craftalism/market/MarketPlugin.java`
    - only consumes config values and passes literal `market-api.auth-token`
  - `craftalism-market/src/main/resources/config.yml`
    - default `base-url` is `http://127.0.0.1:8080`
    - default `auth-token` is empty
  - `craftalism-market/docs/repo-contract-map.md`
    - says market must “obtain and use tokens/configuration consistently with issuer expectations”
  - Economy reference implementation:
    - `craftalism-economy/java/src/main/java/io/github/HenriqueMichelini/craftalism/economy/infra/config/
  ConfigLoader.java`
    - `craftalism-economy/java/src/main/java/io/github/HenriqueMichelini/craftalism/economy/infra/api/
  client/OAuth2TokenService.java`
    - `craftalism-economy/java/src/main/java/io/github/HenriqueMichelini/craftalism/economy/infra/api/
  service/ApiServiceFactory.java`

  Requested change in `craftalism-market`:
  - Implement economy-style auth/config parity in the market plugin.
  - The plugin should read env/config equivalents for:
    - API base URL
    - auth issuer URL
    - token path
    - client id
    - client secret / API key
    - scopes if needed
  - It should mint/cache OAuth2 client-credentials tokens internally instead of requiring a static
  precomputed `market-api.auth-token`.
  - Snapshot, quote, and execute calls should all use that token flow consistently.
  - Keep repo boundaries clean: no deployment-specific hacks in the plugin.

  Goal:
  - After your fix, `craftalism-deployment` should only need to provide the same auth/env inputs already
  used by economy, and market should authenticate successfully on its own.
