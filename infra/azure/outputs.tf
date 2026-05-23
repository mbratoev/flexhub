# Outputs we'll use in the next steps (image push, helm install, deploy workflow).

output "resource_group_name" {
  value       = azurerm_resource_group.main.name
  description = "Resource group all flexhub resources live in."
}

output "acr_login_server" {
  value       = azurerm_container_registry.main.login_server
  description = "ACR login server, e.g. flexhubdevacrabc123.azurecr.io — used in `docker tag` and Helm values."
}

output "aks_get_credentials_cmd" {
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${azurerm_kubernetes_cluster.main.name} --overwrite-existing"
  description = "Paste this into a shell to merge AKS credentials into ~/.kube/config."
}

output "aks_oidc_issuer_url" {
  value       = azurerm_kubernetes_cluster.main.oidc_issuer_url
  description = "AKS OIDC issuer. Already wired into the federated credential; included for debugging."
}

output "eventhubs_kafka_bootstrap" {
  value       = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net:9093"
  description = "Event Hubs Kafka endpoint. Used as SPRING_KAFKA_BOOTSTRAP_SERVERS."
}

output "key_vault_uri" {
  value       = azurerm_key_vault.main.vault_uri
  description = "Key Vault DNS URI, e.g. https://flexhubdevkv...vault.azure.net/"
}

output "key_vault_name" {
  value       = azurerm_key_vault.main.name
  description = "Key Vault name (used in `az keyvault secret show` and in Helm CSI volume mounts)."
}

output "workload_identity_client_id" {
  value       = azurerm_user_assigned_identity.workload.client_id
  description = "Client ID of the managed identity pods authenticate as. Goes into the K8s ServiceAccount annotation."
}

output "subscription_id" {
  value       = data.azurerm_client_config.current.subscription_id
  description = "Azure subscription ID — sanity-check that you're applying to the intended account."
}
