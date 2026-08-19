# rules-java.ps1 - rules that run on .java files.
#
# Rules land here in wave 2, in this order (safest first):
#   cypher-location         Cypher outside a *Repository
#   cypher-concat           query assembled from a non-literal operand
#   exception-unregistered  new *Exception missing from GlobalExceptionHandler
#   page-size-uncapped      *Controller with a size param and no MAX_PAGE_SIZE
#   entity-returned         controller method returning an entity, not a DTO
#   soft-delete             Person read without an isDeleted filter  (LAST, gated
#                           on a clean dry run - it is the only rule with a
#                           non-trivial exemption list, and a misfire here gets
#                           the whole guard switched off)
#
# A rule reports through Add-Violation and never filters for itself: the
# touched-lines check and the guard:allow marker are handled in guard-lib.ps1.

function Invoke-JavaRules {
    param($Ctx)

    # No rules enabled yet. The dry run below must stay silent until wave 2:
    #   powershell -File .claude\hooks\guard.ps1 -Path <file> -All
}
