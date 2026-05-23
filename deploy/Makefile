# Kubernetes workflow shortcuts for the flexhub stack — local (kind) + Azure (AKS).
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
AZURE_TF_DIR    := $(REPO_ROOT)/infra/azure
AZURE_VALUES    := $(VALUES_DIR)/azure

# Read these once per make invocation; lazy so `terraform output` only fires
# when an aks-* target actually uses them.
tf_output       = $(shell terraform -chdir=$(AZURE_TF_DIR) output -raw $(1))

.PHONY: help kind-up load-images infra-install services-install services-helm-install install \
        port-forward smoke-test status uninstall destroy \
        aks-credentials aks-build-push aks-bootstrap-secrets aks-install aks-public-ip aks-uninstall

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

# ─── AKS targets ────────────────────────────────────────────────────────
# All `aks-*` targets assume `terraform apply` in infra/azure has succeeded.
# They read Terraform outputs to discover the ACR / Postgres / Event Hubs
# / Key Vault names without hardcoding any.

aks-credentials: ## Merge AKS credentials into kubeconfig (one-time per cluster).
	@RG=$(call tf_output,resource_group_name); \
	 NAME=flexhub-dev-aks; \
	 az aks get-credentials --resource-group $$RG --name $$NAME --overwrite-existing

aks-build-push: ## Build the four images for linux/amd64 (AKS nodes) and push to ACR.
	@ACR=$(call tf_output,acr_login_server); \
	 az acr login --name $${ACR%%.*}; \
	 for svc in account-service transaction-service notification-service api-gateway; do \
	   docker buildx build --platform linux/amd64 --push \
	     -f $(REPO_ROOT)/services/$$svc/Dockerfile \
	     -t $$ACR/flexhub/$$svc:latest \
	     $(REPO_ROOT); \
	 done

aks-create-service-account: ## Create the shared flexhub-app ServiceAccount once. All four services then reference it.
	@WI_CID=$(call tf_output,workload_identity_client_id); \
	 kubectl apply -f - <<EOF
	apiVersion: v1
	kind: ServiceAccount
	metadata:
	  name: flexhub-app
	  namespace: default
	  labels:
	    azure.workload.identity/use: "true"
	  annotations:
	    azure.workload.identity/client-id: "$$WI_CID"
	EOF

aks-install-postgres: ## helm-install bitnami/postgresql inside AKS. Workaround for free-trial Postgres Flexible Server restrictions.
	helm upgrade --install postgres bitnami/postgresql --version 16.7.27 \
	   -f $(REPO_ROOT)/infra/k8s/postgres-values.yaml
	kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=postgresql --timeout=240s

aks-bootstrap-secrets: ## Pull the Event Hubs JAAS connection string from Key Vault into a k8s Secret.
	@KV=$(call tf_output,key_vault_name); \
	 EH_CS=$$(az keyvault secret show --vault-name $$KV --name eventhubs-connection-string --query value -o tsv); \
	 JAAS="org.apache.kafka.common.security.plain.PlainLoginModule required username=\"\$$ConnectionString\" password=\"$$EH_CS\";"; \
	 kubectl create secret generic flexhub-secrets \
	   --from-literal=eventhubs-jaas-config="$$JAAS" \
	   --dry-run=client -o yaml | kubectl apply -f -

aks-install: ## Helm-install the four services in AKS with terraform-output overrides.
	@ACR=$(call tf_output,acr_login_server); \
	 EH_BOOT=$(call tf_output,eventhubs_kafka_bootstrap); \
	 WI_CID=$(call tf_output,workload_identity_client_id); \
	 PG_BASE="jdbc:postgresql://postgres-postgresql:5432/flexhub?currentSchema"; \
	 for svc in account-service transaction-service notification-service; do \
	   schema=$$(echo $$svc | sed 's/-service$$//; s/account/accounts/; s/transaction/transactions/; s/notification/notifications/'); \
	   helm upgrade --install $$svc $(CHART) -f $(AZURE_VALUES)/$$svc.yaml \
	     --set image.repository=$$ACR/flexhub/$$svc \
	     --set azureWorkloadIdentity.clientId=$$WI_CID \
	     --set azureWorkloadIdentity.createServiceAccount=false \
	     --set config.SPRING_DATASOURCE_URL="$$PG_BASE=$$schema" \
	     --set config.SPRING_KAFKA_BOOTSTRAP_SERVERS=$$EH_BOOT ; \
	 done; \
	 helm upgrade --install api-gateway $(CHART) -f $(AZURE_VALUES)/api-gateway.yaml \
	   --set image.repository=$$ACR/flexhub/api-gateway

aks-public-ip: ## Print the api-gateway's external IP (wait for it if Pending).
	@kubectl get svc api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}' ; \
	 echo "If empty, the LoadBalancer hasn't allocated an IP yet. Retry in 30s."

aks-smoke-test: ## Run the golden-path + compensation saga against the AKS public IP.
	@$(REPO_ROOT)/deploy/aks-smoke-test.sh

aks-start: ## One-shot: terraform apply + all setup + helm install + report gateway IP (~25-35 min).
	@$(REPO_ROOT)/deploy/aks-start.sh

aks-uninstall: ## Helm-uninstall the four services + Postgres chart. Keeps the cluster + Azure infra.
	-helm uninstall api-gateway notification-service transaction-service account-service
	-helm uninstall postgres
	-kubectl delete secret flexhub-secrets --ignore-not-found
	-kubectl delete pvc -l app.kubernetes.io/name=postgresql
