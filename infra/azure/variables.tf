# Variables for the Azure stack. Defaults are reasonable for the EU scenario;
# override via terraform.tfvars or CLI -var flags.

variable "location" {
  description = "Azure region for all resources."
  type        = string
  # westeurope (Amsterdam) — for free-trial subscriptions this is the most
  # broadly permissive EU region: allows both Postgres Flexible Server AND
  # Standard_B-series AKS VMs. germanywestcentral blocks Postgres; northeurope
  # blocks B-series VMs in 2026. ~25ms RTT from Sofia.
  default = "westeurope"
}

variable "name_prefix" {
  description = "Short string prepended to every resource name. Keep it lowercase + alphanumeric — globally-unique resources like ACR are most restrictive."
  type        = string
  default     = "flexhub"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{2,10}$", var.name_prefix))
    error_message = "name_prefix must be 3-11 chars, lowercase alphanumeric, starting with a letter."
  }
}

variable "environment" {
  description = "Short environment label (dev/staging/prod). Joins name_prefix in resource names."
  type        = string
  default     = "dev"
}

variable "aks_node_count" {
  description = "Initial AKS node count for the system+app node pool. Autoscaler can grow it from here."
  type        = number
  default     = 2
}

variable "aks_node_vm_size" {
  description = "VM size for AKS nodes. Standard_B2s_v2 is 2 vCPU / 8 GB; the cheapest viable size for a four-service learning workload in westeurope (the v1 Standard_B2s is no longer available there)."
  type        = string
  default     = "Standard_B2s_v2"
}

variable "tags" {
  description = "Resource tags applied to every resource."
  type        = map(string)
  default = {
    project = "flexhub"
    managed = "terraform"
  }
}
