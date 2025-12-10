This PR enables Renovate automerge for patch and minor updates and adds a workflow which requests GitHub auto-merge for Renovate-created PRs.

Manual repository settings you must verify or change (branch protection / repo settings):

1) Enable "Allow auto-merge" for the repository:
   - Settings → Merge button → Enable Auto-merge.

2) Branch protection rules for the target branch (e.g., main):
   - If "Restrict who can push" is enabled, add the Renovate bot (e.g., renovate[bot] or the Renovate GitHub App) OR the "GitHub Actions" app to the allowed actors list. Either the Renovate app or GitHub Actions must be permitted to perform the final merge.
   - Required status checks: ensure CI checks run and pass on Renovate PRs.
   - Required reviews: if required, ensure a review is approved before auto-merge.

3) Renovate app permissions:
   - Make sure the Renovate GitHub App is installed on this repo and has write permissions for Pull Requests and content as needed.

Why both config and workflow?
- renovate.json config requests GitHub auto-merge for Renovate PRs when updates match the packageRules. Some installations of Renovate do not request auto-merge or you want a safety net: the workflow will request auto-merge when a Renovate PR is opened or updated.

Troubleshooting checklist:
- Confirm PR shows the Auto-merge badge. If not, check required checks and approvals.
- If you see "Merge blocked by branch protection", verify the allowed push actors as noted above.
- Check Renovate logs (if using Renovate App/hosted). For self-hosted, check the Renovate manager logs.
