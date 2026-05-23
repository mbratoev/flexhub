# Log Analytics workspace — AKS sends container stdout/stderr + node metrics
# here via the OMS / Azure Monitor agents. Centralized log aggregation across
# all four pods, plus the kubelet/containerd events that don't surface in
# kubectl logs.

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${local.base}-logs"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30 # minimum for the SKU; cheapest setting
  tags                = var.tags
}
