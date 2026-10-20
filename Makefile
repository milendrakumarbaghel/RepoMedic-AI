.PHONY: setup test build run dev smoke demo-zip record-demo docker-build docker-test

setup:
	node scripts/project.mjs setup

# Offline after setup. Includes frontend tests, lint, typecheck/build and Java tests.
test:
	node scripts/project.mjs test

build:
	node scripts/project.mjs build

run:
	node scripts/project.mjs run

dev:
	node scripts/project.mjs dev

smoke:
	node scripts/smoke.mjs

demo-zip:
	node scripts/demo-zip.mjs

record-demo:
	node scripts/record-demo.mjs

docker-build:
	docker build -t repomedic-ai:local .

docker-test:
	docker build --target test -t repomedic-ai:test .
	docker run --rm --network none repomedic-ai:test make test
