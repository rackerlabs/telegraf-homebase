local-image:
	docker build -t local/homebase .

#TODO: externally configure the POSTGRES_PASSWORD
swarm-up :
	POSTGRES_PASSWORD=secret docker stack deploy -c docker-compose.yml tremote

swarm-down:
	docker stack rm tremote

swarm-status:
	docker stack ps tremote

watch-telegraf-regional-logs:
	docker service logs -f tremote_telegraf-regional --tail 10

watch-telegraf-assigned-logs:
	docker service logs -f tremote_telegraf-assigned --tail 10

watch-homebase-logs:
	docker service logs -f tremote_homebase --tail 10

purge-volumes:
	docker volume rm tremote_influxdb tremote_postgres tremote_cassandra

.PHONY: local-image swarm-up swarm-down swarm-status
.PHONY: watch-telegraf-regional-logs watch-telegraf-assigned-logs watch-homebase-logs
.PHONY: purge-volumes