# rules-frontend.ps1 - rules that run on files under frontend/src/.
#
# Rules land here in wave 2, zero-baseline ones first:
#   legacy-control-flow   *ngIf / *ngFor / CommonModule  (repo is 100% @if/@for)
#   constructor-di        constructor(private x) instead of inject()
#   route-not-lazy        component: or an eager ./features/ import in app.routes.ts
#   http-outside-seam     HttpClient outside core/api - the seam rule that matters
#   component-shape       missing standalone, non-sa- selector, styleUrls/.scss
#   api-token-unbound     new *Api token with no binding in app.config.ts
#   mock-api-bound        useClass: Mock* appearing in app.config.ts
#   hardcoded-color       hex in a component .css   (6 pre-existing files)
#   native-select         <select> instead of sa-select  (2 pre-existing files)
#
# hardcoded-color deliberately skips .ts and .html: Skill.color is a real API
# field, so hexes in mock-data.ts are domain data, and the hexes in templates are
# decorative inline SVG. Catching one real violation there would cost 12 false
# ones - that lesson lives in the new-screen skill instead.

function Invoke-FrontendRules {
    param($Ctx)

    # No rules enabled yet. The dry run below must stay silent until wave 2:
    #   powershell -File .claude\hooks\guard.ps1 -Path <file> -All
}
