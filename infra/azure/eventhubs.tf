# Azure Event Hubs in Kafka-compatible mode — replaces the Bitnami Kafka chart.
#
# Mechanism: Event Hubs namespaces expose a "Kafka endpoint" at port 9093 using
# SASL_SSL + SAS authentication. Spring Kafka clients work unchanged with these
# config additions (set in the Helm values for AKS):
#   spring.kafka.bootstrap-servers = <namespace>.servicebus.windows.net:9093
#   spring.kafka.properties:
#     security.protocol: SASL_SSL
#     sasl.mechanism: PLAIN
#     sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule
#       required username="$ConnectionString" password="<SAS-conn-str>";
#
# Concept mapping: namespace = cluster, event hub = topic, partitions and
# consumer groups identical.
#
# Tier: Standard is required for the Kafka surface AND for consumer groups
# (Basic gives a single $Default group). ~€20/mo at idle.

resource "azurerm_eventhub_namespace" "main" {
  name                = "${local.base}-eh-${local.suffix}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  sku      = "Standard"
  capacity = 1 # 1 TU = 1 MB/s ingress, 2 MB/s egress — easily enough for the saga

  # Auto-inflate would scale TUs under load. Disabled to keep cost predictable
  # in a learning environment.
  auto_inflate_enabled = false

  tags = var.tags
}

# One event hub per topic. Matches the topics created by NewTopic beans in
# the application; we have to declare them up front here because Event Hubs
# doesn't auto-create on producer/consumer access (and we shouldn't rely on
# auto-create even if it did).
locals {
  saga_topics = [
    "transfers.requested",
    "transfers.completed",   # legacy aggregate event, still declared for compat
    "transfers.reversed",
    "accounts.debited",
    "accounts.debit-rejected",
    "accounts.credited",
    "accounts.credit-failed",
  ]
}

resource "azurerm_eventhub" "topics" {
  for_each = toset(local.saga_topics)

  name              = each.value
  namespace_id      = azurerm_eventhub_namespace.main.id
  partition_count   = 1
  message_retention = 1 # 1 day — cheapest setting; saga is realtime, no replay needed
}

# Namespace-scoped SAS rule with Send + Listen for application services. The
# resulting connection string goes into Key Vault (see keyvault.tf).
resource "azurerm_eventhub_namespace_authorization_rule" "app" {
  name                = "app-saga"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = azurerm_resource_group.main.name

  listen = true
  send   = true
  manage = false
}
