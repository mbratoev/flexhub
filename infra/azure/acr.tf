# Azure Container Registry — private registry for the four service images.
#
# Naming: must be 5-50 alphanumeric chars, globally DNS-unique. Suffix the
# random string to dodge collisions across all Azure tenants.
#
# Tier: Basic (~€4/mo) is enough for four images and infrequent pulls.
# Premium adds geo-replication + private endpoints; not needed here.

resource "azurerm_container_registry" "main" {
  name                = "${local.base_dense}acr${local.suffix}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Basic"
  admin_enabled       = false # AKS pulls via managed identity; no need for admin creds
  tags                = var.tags
}
