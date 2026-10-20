package org.springboot.backend;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
class GitHubImporter {
    private static final Pattern URL = Pattern.compile("^https://github\\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9_.-]{1,100})/?$");
    private final RemoteHttp remote;
    private final JsonMapper json;
    private final ZipIngestor zip;

    record Repository(String name, String branch) { }

    GitHubImporter(RemoteHttp remote, JsonMapper json, ZipIngestor zip) {
        this.remote = remote; this.json = json; this.zip = zip;
    }

    static String repository(String url) {
        if (url == null) throw ApiException.invalid("A public https://github.com/owner/repo URL is required");
        var match = URL.matcher(url);
        if (!match.matches()) throw ApiException.invalid("Use a public https://github.com/owner/repo URL without query, fragment, or subpath");
        String repo = match.group(2).replaceFirst("\\.git$", "");
        if (repo.isBlank() || repo.equals(".") || repo.equals("..")) throw ApiException.invalid("Invalid GitHub repository");
        return match.group(1) + "/" + repo;
    }

    Repository fetch(String url, Path directory, Path workspace) {
        String name = repository(url);
        RemoteHttp.Response metadata = remote.send(request("https://api.github.com/repos/" + name), 256 * 1024);
        check(metadata.status());
        String branch;
        try {
            var tree = json.readTree(metadata.body());
            if (tree.path("private").asBoolean(false)) throw ApiException.invalid("Only public repositories are supported");
            branch = tree.path("default_branch").asString("");
        } catch (ApiException e) { throw e; }
        catch (RuntimeException e) { throw new ApiException(502, "github_error", "GitHub returned invalid repository metadata"); }
        if (branch.isBlank() || branch.length() > 200) throw ApiException.invalid("Repository has no usable default branch");
        String encoded = URLEncoder.encode(branch, StandardCharsets.UTF_8).replace("+", "%20");
        RemoteHttp.Response archive = remote.send(request("https://codeload.github.com/" + name + "/zip/refs/heads/" + encoded),
                (int) ZipIngestor.MAX_COMPRESSED);
        check(archive.status());
        zip.extract(new ByteArrayInputStream(archive.body()), directory, workspace, true);
        return new Repository(name, branch);
    }

    private HttpRequest request(String uri) {
        return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "RepoMedic-AI").header("Accept", "application/vnd.github+json").GET().build();
    }

    private void check(int status) {
        if (status == 404) throw ApiException.invalid("Public GitHub repository or default branch was not found");
        if (status == 403 || status == 429) throw new ApiException(429, "github_rate_limit", "GitHub rate limit reached; retry later");
        if (status < 200 || status >= 300) throw new ApiException(502, "github_error", "GitHub request failed; redirects are not followed");
    }
}
