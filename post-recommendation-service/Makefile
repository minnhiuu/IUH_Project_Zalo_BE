.PHONY: run test lint

run:
	uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8091

test:
	uv run pytest -q

lint:
	uv run ruff check .
