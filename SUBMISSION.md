# RepoMedic AI — submission checklist

## Public project

- Repository: `https://github.com/milendrakumarbaghel/RepoMedic-AI`
- Default branch: `main`
- Main README: [`README.md`](README.md)
- Demo recording: [`assets/repomedic-demo.mov`](assets/repomedic-demo.mov)
- Rebuild evidence: [`reports/reproducibility.md`](reports/reproducibility.md)
- Diagnostic report: [`reports/demo-diagnostic.md`](reports/demo-diagnostic.md)
- Patch artifact: [`reports/demo-fix.patch`](reports/demo-fix.patch)

## Winning demo path

1. Run `docker build -t repomedic-ai:local .`.
2. Run `docker run --rm -p 127.0.0.1:8080:8080 repomedic-ai:local`.
3. Open `http://localhost:8080`.
4. Choose **Try broken React demo**.
5. Confirm the real baseline is **3 passed / 3 failed**.
6. Open **Patch preview** and review `cart.js:13`.
7. Apply the patch and rerun tests.
8. Confirm **6 passed / 0 failed** and the baseline remains available under
   **Test output**.

## Review notes

- The README is intentionally explicit about local deployment because no hosted
  service is required or claimed.
- The demo fix is labelled **Rule-based**, not falsely described as AI-generated.
- Uploaded projects are static-only by default; arbitrary uploaded code is not
  executed on the host.
- A GitHub import with missing tests reports **blocked**, never fake success.
- The public demo video is an actual recording of the application, not a mockup.

## Final upload checklist

Before the hackathon deadline, confirm the platform submission form includes:

- The public GitHub URL above.
- The repository README URL.
- The demo recording URL or uploaded `assets/repomedic-demo.mov` file.
- The local link note, if the platform requires a live URL.
- The final commit hash shown by `git log -1 --format=%H` after the upload.
