# Resource group + a global-uniqueness suffix used by name-restricted resources
# (ACR, Key Vault, Postgres server, Event Hubs namespace — all need DNS-unique
# names across all of Azure).

resource "random_string" "suffix" {
  length  = 6
  upper   = false
  special = false
  numeric = true
}

locals {
  # Composite naming: <prefix>-<env>-<resource> for grouped resources,
  # <prefix><env><resource><suffix> for globally-unique ones (no hyphens
  # for resources like ACR that don't allow them).
  base       = "${var.name_prefix}-${var.environment}"
  base_dense = "${var.name_prefix}${var.environment}"
  suffix     = random_string.suffix.result
}

resource "azurerm_resource_group" "main" {
  name     = "${local.base}-rg"
  location = var.location
  tags     = var.tags
}
