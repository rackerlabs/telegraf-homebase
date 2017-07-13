local-image:
	docker build -t local/homebase .

swarm-deploy :
	docker stack deploy -c docker-compose.yml tremote

swarm-undeploy:
	docker stack rm tremote

status:
	docker stack ps tremote

watch-telegraf-logs:
	docker service logs -f tremote_telegraf --tail 0

watch-homebase-logs:
	docker service logs -f tremote_homebase --tail 0

.PHONY: local-image