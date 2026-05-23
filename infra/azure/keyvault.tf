# Azure Key Vault — stores the Postgres admin password and the Event Hubs
# Kafka SAS connection string. Pods access these at runtime via workload
# identity (no static secrets in Kubernetes manifests / ConfigMaps).
#
# Naming: 3-24 chars, globally DNS-unique. Suffix the random string.

data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "main" {
  name                = substr("${local.base_dense}kv${local.suffix}", 0, 24)
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tenant_id           = data.azurerm_client_config.current.tenant_id

  sku_name = "standard"

  # RBAC over access policies — modern Azure pattern. Lets us grant access
  # by role assignment instead of Vault-internal policy objects, which fits
  # the rest of our IAM model (managed identities + role assignments).
  rbac_authorization_enabled = true

  # Public access on; restrict by RBAC. Production would add network ACLs +
  # private endpoint as a hardening step.
  public_network_access_enabled = true

  tags = var.tags
}

# Grant the running az-login user "Key Vault Secrets Officer" so Terraform
# (running as that identity) can WRITE the secrets below. Without this, we'd
# create the vault but be unable to populate it in the same apply.
resource "azurerm_role_assignment" "kv_admin" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

# Event Hubs Kafka connection string — what the apps put in
# spring.kafka.properties.sasl.jaas.config as the password.
resource "azurerm_key_vault_secret" "eventhubs_connection_string" {
  name         = "eventhubs-connection-string"
  value        = azurerm_eventhub_namespace_authorization_rule.app.primary_connection_string
  key_vault_id = azurerm_key_vault.main.id

  depends_on = [azurerm_role_assignment.kv_admin]
}

# NOTE: A `postgres-admin-password` secret would normally live here, populated
# from `random_password.postgres_admin` in postgres.tf. Removed because
# free-trial subscriptions block Postgres Flexible Server in every EU region
# we tried (germanywestcentral, westeurope), so we use the Bitnami Postgres
# Helm chart inside AKS instead — its password lives in a k8s Secret created
# by the chart, not in Key Vault. Restore this secret + postgres.tf when the
# subscription is upgraded to PAYG.
