# Workload identity setup — what lets pods authenticate to Azure resources
# (specifically Key Vault) using their Kubernetes ServiceAccount, with NO
# secrets stored anywhere.
#
# The mechanism:
#   1. We create a User-Assigned Managed Identity in Azure.
#   2. We federate it to a specific Kubernetes ServiceAccount via AKS's OIDC
#      issuer URL. The federated credential says "trust tokens issued by this
#      AKS cluster's OIDC issuer for this exact serviceaccount in this exact
#      namespace."
#   3. The ServiceAccount in K8s is annotated with the managed identity's
#      client_id. When a pod uses that SA, the workload-identity webhook
#      injects an OIDC token; the Azure SDK exchanges it for a real Azure
#      access token without ever seeing a static credential.
#
# We grant the managed identity "Key Vault Secrets User" so pods can read
# secrets (read-only — they can't modify the vault).

resource "azurerm_user_assigned_identity" "workload" {
  name                = "${local.base}-workload-identity"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = var.tags
}

# Federation: trust the AKS OIDC issuer to mint tokens for the
# "default:flexhub-app" ServiceAccount. The "subject" string is the K8s
# `system:serviceaccount:<namespace>:<name>` identifier the SA presents
# inside the cluster.
resource "azurerm_federated_identity_credential" "aks_app_sa" {
  # `resource_group_name` was removed in azurerm v4 — the resource is fully
  # identified by parent_id (the User-Assigned Managed Identity it federates).
  name      = "aks-flexhub-app"
  parent_id = azurerm_user_assigned_identity.workload.id

  audience = ["api://AzureADTokenExchange"] # required magic string
  issuer   = azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject  = "system:serviceaccount:default:flexhub-app"
}

# Read-only access to ALL secrets in the vault for the managed identity.
resource "azurerm_role_assignment" "workload_kv_reader" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.workload.principal_id
}
