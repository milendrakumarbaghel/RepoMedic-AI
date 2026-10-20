# Deliberately broken React cart

Run `make demo-zip` from the repository root. It generates
`examples/broken-react.zip` directly from the four audited source resources in
`backend/src/main/resources/demo/`. The same ZIP is included in the built UI at
`/demo/broken-react.zip`. Generated binary archives are not committed.

Upload this ZIP in RepoMedic: three of six real tests fail. Review the source
finding and side-by-side patch, then choose **Apply patch & rerun tests**. All
six tests pass. Download the report to compare the preserved baseline and rerun.

The backend recognizes this exact fixture by its complete contents, not its
filename. Editing the tests or adding any extra file removes host-execution
trust. Other projects remain static-only unless a separate Docker sandbox is
explicitly enabled. The one audited fixed version is recognized too.
