# Provider configuration for the Azure stack.
#
# Authentication: relies on `az login` having been run. The azurerm provider
# picks up the current az context — no service principal secrets in source.
# CI deploys would use OIDC federated credentials instead.
#
# State: LOCAL state. terraform.tfstate lives next to these files and is
# gitignored. Suitable for solo dev; upgrade to a remote backend later by
# adding `backend "azurerm" { ... }` here + `terraform init -migrate-state`.

terraform {
  required_version = ">= 1.6"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "azurerm" {
  features {
    # When `terraform destroy` runs, also delete the Key Vault permanently
    # rather than soft-delete (which would block re-creating with the same name
    # for 90 days). Fine for a learning project; production keeps soft delete.
    key_vault {
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = true
    }
    resource_group {
      # Don't fail destroy when there are resources still in the RG. Combined
      # with our explicit dependencies this just makes teardown more forgiving.
      prevent_deletion_if_contains_resources = false
    }
  }
}
