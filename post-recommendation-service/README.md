# Post Recommendation Service (Minimal Starter)

Bare-minimum FastAPI project with Eureka registration logic kept in place.

## Run

```bash
uv sync
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8091
```

## Health

```bash
curl http://localhost:8091/health
```

## Eureka

Set in `.env`:

```env
APP_NAME=post-recommendation-service
EUREKA_ENABLED=true
EUREKA_SERVER_URL=http://localhost:8761/eureka/
```

Optional for local machine registration visibility:

```env
EUREKA_INSTANCE_HOST=localhost
```

## Qdrant

Qdrant is available from the backend compose stack at `localhost:6333` (HTTP) and `localhost:6334` (gRPC).

Set in `.env`:

```env
QDRANT_HOST=localhost
QDRANT_PORT=6333
QDRANT_GRPC_PORT=6334
QDRANT_HTTPS=false
QDRANT_TIMEOUT_SECONDS=5.0
```

Use the client factory in code:

```python
from app.clients.qdrant_client import get_qdrant_client

client = get_qdrant_client()
collections = client.get_collections()
```

## MongoDB

MongoDB is available from the backend compose stack on `localhost:27018`.

Set in `.env`:

```env
MONGODB_URI=mongodb://localhost:27018
MONGODB_DATABASE=post_recommendation
MONGODB_SERVER_SELECTION_TIMEOUT_MS=3000
```

Use the client factory in code:

```python
from app.clients.mongodb_client import get_mongodb_database

db = get_mongodb_database()
posts_collection = db["posts"]
```
