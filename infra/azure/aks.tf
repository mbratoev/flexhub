# AKS cluster. Three things differentiate this from a plain `az aks create`:
#
# 1. `oidc_issuer_enabled` + `workload_identity_enabled` — turns on AKS's
#    OIDC issuer so pods can authenticate to Azure resources via federated
#    credentials. The managed identity in identity.tf trusts this issuer to
#    mint tokens for a specific Kubernetes ServiceAccount.
#
# 2. `oms_agent` — ships container stdout/stderr to Log Analytics. Enables
#    Container Insights in the portal so we can browse logs the same way we
#    'kubectl logs' locally but without a kubeconfig context switch.
#
# 3. The cluster-managed identity gets AcrPull on our ACR via a role
#    assignment below. AKS uses this identity for image pulls; no docker
#    login / pull-secret faff like with self-hosted clusters.

resource "azurerm_kubernetes_cluster" "main" {
  name                = "${local.base}-aks"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = "${local.base}-aks"

  # Required for workload identity federation between AKS and Azure AD.
  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  default_node_pool {
    name = "default"
    # We disable autoscaling at first to keep the bill predictable; HPA at the
    # pod level handles bursts. Flip enable_auto_scaler=true + min/max for
    # node-level autoscaling later.
    node_count = var.aks_node_count
    vm_size    = var.aks_node_vm_size
    # Default os_disk_type=Managed (P15-class disk). Ephemeral OS would be
    # faster but Standard_B2s only has 32 GB cache and AKS defaults to a
    # 128 GB OS disk — won't fit.
    tags = var.tags
  }

  # AKS itself runs as a system-assigned managed identity — gets ACR pull
  # rights via the role assignment below.
  identity {
    type = "SystemAssigned"
  }

  # Ship container logs + metrics to the Log Analytics workspace from monitoring.tf.
  oms_agent {
    log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  }

  tags = var.tags
}

# AKS's kubelet identity needs AcrPull on the ACR so it can pull images
# without us managing imagePullSecrets in K8s manifests.
resource "azurerm_role_assignment" "aks_acr_pull" {
  principal_id                     = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
  role_definition_name             = "AcrPull"
  scope                            = azurerm_container_registry.main.id
  skip_service_principal_aad_check = true
}
