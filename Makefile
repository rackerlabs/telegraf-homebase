local-image:
	docker build -t local/homebase .

swarm-up :
	docker stack deploy -c docker-compose.yml tremote

swarm-down:
	docker stack rm tremote

swarm-status:
	docker stack ps tremote

watch-telegraf-logs:
	docker service logs -f tremote_telegraf --tail 0

watch-homebase-logs:
	docker service logs -f tremote_homebase --tail 0

.PHONY: local-image