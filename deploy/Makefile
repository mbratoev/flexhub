# Kubernetes workflow shortcuts for the flexhub stack on a local kind cluster.
#
# Run from repo root:   make -C deploy <target>
# Or run any individual command shown below by hand.

REPO_ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
CLUSTER   := flexhub
IMAGES    := \
	flexhub-account-service:latest \
	flexhub-transaction-service:latest \
	flexhub-notification-service:latest \
	flexhub-api-gateway:latest

KAFKA_VALUES    := $(REPO_ROOT)/infra/k8s/kafka-values.yaml
POSTGRES_VALUES := $(REPO_ROOT)/infra/k8s/postgres-values.yaml
KIND_CONFIG     := $(REPO_ROOT)/infra/k8s/kind-config.yaml
CHART           := $(REPO_ROOT)/deploy/helm/flexhub-service
VALUES_DIR      := $(REPO_ROOT)/deploy/helm/values

.PHONY: help kind-up load-images infra-install services-install services-helm-install install \
        port-forward smoke-test status uninstall destroy

help: ## Show this help.
	@awk 'BEGIN{FS=":.*##"} /^[a-zA-Z_-]+:.*##/{printf "  %-22s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

kind-up: ## Create the kind cluster (idempotent).
	@kind get clusters | grep -qx $(CLUSTER) || kind create cluster --config $(KIND_CONFIG)
	@kubectl config use-context kind-$(CLUSTER)

load-images: ## Load the four locally-built service images into the kind cluster.
	kind load docker-image --name $(CLUSTER) $(IMAGES)

infra-install: ## Helm-install bitnami/kafka and bitnami/postgresql.
	helm upgrade --install kafka    bitnami/kafka       --version 32.4.3 -f $(KAFKA_VALUES)
	helm upgrade --install postgres bitnami/postgresql  --version 16.7.27 -f $(POSTGRES_VALUES)
	kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=kafka      --timeout=240s
	kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=postgresql --timeout=240s

services-install: ## Apply the four raw manifests (deploy/k8s/) — the "see-the-objects" path.
	kubectl apply -f $(REPO_ROOT)/deploy/k8s/

services-helm-install: ## Helm-install the four services from the shared chart.
	helm upgrade --install account-service      $(CHART) -f $(VALUES_DIR)/account-service.yaml
	helm upgrade --install transaction-service  $(CHART) -f $(VALUES_DIR)/transaction-service.yaml
	helm upgrade --install notification-service $(CHART) -f $(VALUES_DIR)/notification-service.yaml
	helm upgrade --install api-gateway          $(CHART) -f $(VALUES_DIR)/api-gateway.yaml

install: kind-up load-images infra-install services-helm-install ## Bring everything up from scratch (kind + infra + services via Helm).
	@echo "Stack is up. Run 'make -C deploy port-forward' next."

port-forward: ## Forward localhost:8080 to the api-gateway pod (Ctrl-C to stop).
	kubectl port-forward svc/api-gateway 8080:8080

smoke-test: ## Run the golden-path + compensation saga via curl.
	@$(REPO_ROOT)/deploy/smoke-test.sh

status: ## Show pods, services, HPA, helm releases.
	kubectl get pods,svc,hpa
	@echo
	helm list

uninstall: ## Helm-uninstall services + infra, keeping the cluster.
	-helm uninstall api-gateway notification-service transaction-service account-service
	-helm uninstall kafka postgres
	-kubectl delete pvc -l app.kubernetes.io/name=postgresql
	-kubectl delete pvc -l app.kubernetes.io/name=kafka

destroy: ## Delete the entire kind cluster.
	kind delete cluster --name $(CLUSTER)
